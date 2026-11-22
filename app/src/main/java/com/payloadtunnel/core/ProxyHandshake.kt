package com.payloadtunnel.core

import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.TunnelMode
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** A connected socket ready for SSH plus any bytes already read past the HTTP responses. */
class PreparedSocket(val socket: Socket, val leftover: ByteArray)

class HandshakeException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Step 1 + 2 of the connection flow: open a TCP socket to the remote proxy (or the SSH
 * host), send the payload and swallow HTTP responses until the server switches protocols
 * (101) or the SSH banner arrives.
 */
class ProxyHandshake(
    private val profile: Profile,
    private val userAgent: String,
    /** Called with the fresh unconnected socket so the VPN service can protect() it. */
    private val protect: (Socket) -> Unit,
    private val onSendingPayload: () -> Unit,
    /** Receives the socket while it is being set up so a user cancel can close it. */
    private val register: (Socket?) -> Unit,
) {
    fun open(): PreparedSocket {
        val proxyHost = profile.proxyHost
        val proxyPort = profile.proxyPort
        val useProxy = profile.mode.usesProxy && !profile.mode.usesTls && !proxyHost.isNullOrBlank() && proxyPort != null
        val host = if (useProxy) proxyHost!! else profile.sshHost
        val port = if (useProxy) proxyPort!! else profile.sshPort

        if (profile.mode.usesProxy && !useProxy) {
            AppLog.w("No remote proxy set, connecting directly to the SSH host")
        }
        AppLog.i(if (useProxy) "Connecting to proxy $host:$port" else "Connecting to SSH host $host:$port")

        var socket = Socket()
        register(socket)
        protect(socket)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw HandshakeException(describeConnectError(e, host, port, useProxy), e)
        }
        AppLog.ok("TCP connected to ${socket.inetAddress?.hostAddress ?: host}:$port")

        if (profile.mode.usesTls) {
            val sni = profile.sni.ifBlank { profile.sshHost }
            AppLog.i("TLS handshake (SNI: $sni)")
            socket = try {
                Tls.wrap(socket, sni, port).also { register(it) }
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw HandshakeException("TLS handshake with $host:$port failed (SNI $sni): ${e.message ?: e.javaClass.simpleName}", e)
            }
            AppLog.ok("TLS established (${(socket as javax.net.ssl.SSLSocket).session.protocol})")
        }

        val template = when {
            !profile.mode.usesPayload -> ""
            profile.payload.isNotBlank() -> profile.payload
            profile.mode == TunnelMode.SSH_PROXY -> "CONNECT [host_port] [protocol][crlf][crlf]"
            else -> ""
        }
        if (template.isBlank()) {
            return PreparedSocket(socket, ByteArray(0))
        }

        onSendingPayload()
        try {
            val parts = Payload.build(template, profile.sshHost, profile.sshPort, userAgent)
            val out = socket.getOutputStream()
            var total = 0
            parts.forEachIndexed { i, part ->
                out.write(part.bytes)
                out.flush()
                total += part.bytes.size
                if (parts.size > 1) AppLog.i("Payload part ${i + 1}/${parts.size} sent (${part.bytes.size} bytes)")
                if (i < parts.size - 1 && part.delayAfterMs > 0) Thread.sleep(part.delayAfterMs)
            }
            AppLog.ok("Payload sent ($total bytes): ${Payload.printable(parts.fold(ByteArray(0)) { a, p -> a + p.bytes }).take(400)}")
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw HandshakeException("Failed to send payload: ${e.message ?: e.javaClass.simpleName}", e)
        }

        val leftover = try {
            readResponses(socket)
        } catch (e: HandshakeException) {
            runCatching { socket.close() }
            throw e
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw HandshakeException("Error reading server response: ${e.message ?: e.javaClass.simpleName}", e)
        }
        socket.soTimeout = 0
        return PreparedSocket(socket, leftover)
    }

    private fun readResponses(socket: Socket): ByteArray {
        val input: InputStream = socket.getInputStream()
        var buf = ByteArray(0)
        val tmp = ByteArray(16 * 1024)
        var responses = 0
        var lastStatus: String? = null
        var bodyToSkip = 0L
        var totalRead = 0L
        socket.soTimeout = FIRST_RESPONSE_TIMEOUT_MS

        while (true) {
            // Consume as much of the buffer as possible.
            var progressed = true
            while (progressed && buf.isNotEmpty()) {
                progressed = false
                if (bodyToSkip > 0) {
                    val n = minOf(bodyToSkip, buf.size.toLong()).toInt()
                    buf = buf.copyOfRange(n, buf.size)
                    bodyToSkip -= n
                    progressed = true
                    continue
                }
                if (startsWith(buf, SSH)) {
                    val nl = indexOf(buf, NL)
                    val banner = if (nl > 0) String(buf, 0, nl, Charsets.ISO_8859_1).trim() else "SSH-"
                    AppLog.ok("SSH banner received: $banner")
                    return buf
                }
                if (startsWith(buf, HTTP)) {
                    var end = indexOf(buf, CRLFCRLF)
                    var sepLen = 4
                    if (end < 0) {
                        end = indexOf(buf, LFLF)
                        sepLen = 2
                    }
                    if (end < 0) break // need more data
                    val header = String(buf, 0, end, Charsets.ISO_8859_1)
                    val statusLine = header.lineSequence().first().trim()
                    val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                    responses++
                    lastStatus = statusLine
                    buf = buf.copyOfRange(end + sepLen, buf.size)
                    if (code == 101) {
                        AppLog.ok("Server response: $statusLine")
                        return buf
                    }
                    if (code in 200..399) AppLog.i("Server response: $statusLine (ignored)")
                    else AppLog.w("Server response: $statusLine (ignored)")
                    val cl = CONTENT_LENGTH.find(header)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    if (cl > 0) bodyToSkip = cl
                    socket.soTimeout = NEXT_RESPONSE_TIMEOUT_MS
                    progressed = true
                    continue
                }
                if (!couldBePrefix(buf)) {
                    val nl = indexOf(buf, NL)
                    if (nl >= 0) {
                        val line = String(buf, 0, nl, Charsets.ISO_8859_1).trim()
                        if (line.isNotEmpty()) AppLog.i("Ignoring: ${line.take(120)}")
                        buf = buf.copyOfRange(nl + 1, buf.size)
                        progressed = true
                        continue
                    }
                    if (buf.size > 4096) {
                        AppLog.w("Unrecognised data from server, handing it to SSH")
                        return buf
                    }
                }
            }

            if (responses > MAX_RESPONSES || totalRead > MAX_BYTES) {
                AppLog.w("Too many responses from server, starting SSH anyway")
                return buf
            }

            val n = try {
                input.read(tmp)
            } catch (e: SocketTimeoutException) {
                if (responses > 0) {
                    AppLog.i("No further response after \"$lastStatus\", starting SSH")
                } else {
                    AppLog.w("No response to payload within ${FIRST_RESPONSE_TIMEOUT_MS / 1000}s, starting SSH anyway")
                }
                return buf
            }
            if (n < 0) {
                throw HandshakeException(
                    if (lastStatus != null) "Server closed the connection after \"$lastStatus\". Check the payload and proxy."
                    else "Server closed the connection right after the payload. Check the payload, proxy and SSH port.",
                )
            }
            totalRead += n
            buf += tmp.copyOfRange(0, n)
        }
    }

    private fun couldBePrefix(buf: ByteArray): Boolean {
        fun prefixOf(target: ByteArray): Boolean {
            val n = minOf(buf.size, target.size)
            for (i in 0 until n) if (buf[i] != target[i]) return false
            return true
        }
        return prefixOf(SSH) || prefixOf(HTTP)
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val FIRST_RESPONSE_TIMEOUT_MS = 15_000
        const val NEXT_RESPONSE_TIMEOUT_MS = 5_000
        const val MAX_RESPONSES = 20
        const val MAX_BYTES = 1L shl 20

        private val SSH = "SSH-".toByteArray()
        private val HTTP = "HTTP/".toByteArray()
        private val CRLFCRLF = "\r\n\r\n".toByteArray()
        private val LFLF = "\n\n".toByteArray()
        private val NL = "\n".toByteArray()
        private val CONTENT_LENGTH = Regex("(?im)^content-length:\\s*(\\d+)")

        fun startsWith(buf: ByteArray, prefix: ByteArray): Boolean {
            if (buf.size < prefix.size) return false
            for (i in prefix.indices) if (buf[i] != prefix[i]) return false
            return true
        }

        fun indexOf(buf: ByteArray, needle: ByteArray): Int {
            outer@ for (i in 0..buf.size - needle.size) {
                for (j in needle.indices) if (buf[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }

        fun describeConnectError(e: Exception, host: String, port: Int, proxy: Boolean): String {
            val what = if (proxy) "proxy" else "SSH host"
            return when (e) {
                is java.net.UnknownHostException -> "Cannot resolve $what \"$host\". Check the host name and your mobile data / Wi-Fi."
                is java.net.ConnectException -> "Connection to $what $host:$port refused or unreachable (${e.message})"
                is SocketTimeoutException -> "Timed out connecting to $what $host:$port"
                is java.net.NoRouteToHostException -> "No route to $what $host:$port. Is the device online?"
                else -> "Cannot connect to $what $host:$port: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
}
