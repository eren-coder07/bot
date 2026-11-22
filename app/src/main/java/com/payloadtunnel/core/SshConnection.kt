package com.payloadtunnel.core

import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.FlowControlledInput
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import com.payloadtunnel.data.Profile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/** Step 3: SSH over the already-negotiated socket (JSch with a custom socket factory). */
class SshConnection private constructor(private val session: Session) : Upstream {

    override val isConnected: Boolean get() = session.isConnected
    override val description: String get() = "SSH " + (serverVersion ?: "")
    val serverVersion: String? get() = runCatching { session.serverVersion }.getOrNull()

    /** Opens a direct-tcpip channel (what "ssh -D" does for each SOCKS request). */
    override fun open(host: String, port: Int, timeoutMs: Int): StreamChannel = openDirect(host, port, timeoutMs)

    fun openDirect(host: String, port: Int, timeoutMs: Int = 15_000): DirectChannel {
        val ch = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        ch.setHost(host)
        ch.setPort(port)
        ch.setOrgIPAddress("127.0.0.1")
        ch.setOrgPort(0)
        // Own receive buffer with per-channel flow control (see FlowControlledInput).
        val input = FlowControlledInput.attach(ch, WINDOW, PACKET, BUFFER_HIGH, BUFFER_LOW)
        val output = ch.outputStream
        try {
            ch.connect(timeoutMs)
        } catch (e: Exception) {
            input.close()
            ch.disconnect()
            throw e
        }
        return DirectChannel(ch, input, output)
    }

    override fun close() {
        runCatching { session.disconnect() }
    }

    companion object {
        init {
            JSch.setLogger(object : Logger {
                override fun isEnabled(level: Int) = level >= Logger.INFO
                override fun log(level: Int, message: String?) {
                    Log.println(if (level >= Logger.WARN) Log.WARN else Log.DEBUG, "JSch", message ?: "")
                }
            })
        }

        fun connect(profile: Profile, prepared: PreparedSocket): SshConnection {
            val jsch = JSch()
            val session = jsch.getSession(profile.username, profile.sshHost, profile.sshPort)
            session.setPassword(profile.password)
            session.userInfo = PasswordUserInfo(profile.password)

            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            // Keep compatibility with older servers (Dropbear / legacy OpenSSH).
            session.setConfig("server_host_key", withExtra(JSch.getConfig("server_host_key"), "ssh-rsa", "ssh-dss"))
            session.setConfig(
                "kex",
                withExtra(
                    JSch.getConfig("kex"),
                    "diffie-hellman-group14-sha1", "diffie-hellman-group-exchange-sha1", "diffie-hellman-group1-sha1",
                ),
            )
            val ciphers = arrayOf("aes128-cbc", "aes192-cbc", "aes256-cbc", "3des-cbc")
            session.setConfig("cipher.s2c", withExtra(JSch.getConfig("cipher.s2c"), *ciphers))
            session.setConfig("cipher.c2s", withExtra(JSch.getConfig("cipher.c2s"), *ciphers))
            session.setConfig("compression.s2c", "none")
            session.setConfig("compression.c2s", "none")

            session.setSocketFactory(object : SocketFactory {
                override fun createSocket(host: String?, port: Int): Socket = prepared.socket
                override fun getInputStream(socket: Socket): InputStream =
                    PrefixedInputStream(prepared.leftover, socket.getInputStream())
                override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
            })
            session.setServerAliveInterval(KEEPALIVE_MS)
            session.setServerAliveCountMax(KEEPALIVE_COUNT)

            try {
                session.connect(CONNECT_TIMEOUT_MS)
            } catch (e: JSchException) {
                runCatching { session.disconnect() }
                runCatching { prepared.socket.close() }
                throw IOException(describe(e), e)
            }
            return SshConnection(session)
        }

        private fun withExtra(base: String?, vararg extra: String): String {
            val list = (base ?: "").split(',').filter { it.isNotBlank() }.toMutableList()
            extra.forEach { if (it !in list) list += it }
            return list.joinToString(",")
        }

        fun describe(e: Throwable): String {
            val msg = e.message ?: e.javaClass.simpleName
            return when {
                msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) ->
                    "SSH authentication failed: wrong username or password (or the account expired)"
                msg.contains("closed by foreign host", true) || msg.contains("connection is closed", true) ->
                    "Server closed the connection during the SSH handshake. The payload / proxy may be wrong or the server is down."
                msg.contains("timeout", true) ->
                    "SSH handshake timed out. The server did not answer like an SSH server."
                msg.contains("invalid server's version", true) ->
                    "The server did not send a valid SSH banner. Check the SSH port, payload and proxy."
                msg.contains("Algorithm negotiation fail", true) ->
                    "SSH algorithm negotiation failed (server uses unsupported ciphers): $msg"
                else -> "SSH error: $msg"
            }
        }

        const val WINDOW = 512 * 1024
        const val PACKET = 32 * 1024
        const val BUFFER_HIGH = 1024 * 1024
        const val BUFFER_LOW = 256 * 1024
        const val KEEPALIVE_MS = 15_000
        const val KEEPALIVE_COUNT = 3
        const val CONNECT_TIMEOUT_MS = 30_000
    }

    private class PasswordUserInfo(private val password: String) : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = password
        override fun promptPassword(message: String?) = true
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = true
        override fun showMessage(message: String?) {
            if (!message.isNullOrBlank()) AppLog.i("Server message: ${message.trim().take(300)}")
        }

        override fun promptKeyboardInteractive(
            destination: String?, name: String?, instruction: String?,
            prompt: Array<out String>?, echo: BooleanArray?,
        ): Array<String> = Array(prompt?.size ?: 0) { password }
    }
}

class DirectChannel(
    private val channel: ChannelDirectTCPIP,
    override val input: InputStream,
    override val output: OutputStream,
) : StreamChannel {
    override fun close() {
        runCatching { input.close() }
        runCatching { channel.disconnect() }
    }
}

/** Replays bytes read during the HTTP handshake before continuing with the socket stream. */
class PrefixedInputStream(prefix: ByteArray, private val inner: InputStream) : InputStream() {
    private val prefix = prefix
    private var pos = 0

    override fun read(): Int {
        if (pos < prefix.size) return prefix[pos++].toInt() and 0xff
        return inner.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos < prefix.size) {
            val n = minOf(len, prefix.size - pos)
            System.arraycopy(prefix, pos, b, off, n)
            pos += n
            return n
        }
        return inner.read(b, off, len)
    }

    override fun available(): Int = (prefix.size - pos) + inner.available()
    override fun close() = inner.close()
}
