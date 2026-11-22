package com.payloadtunnel.core

import com.payloadtunnel.data.Profile
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * VLESS over WebSocket over TLS (as used by Xray / v2ray). Every SOCKS connection gets its own
 * TLS + WebSocket connection to the server, carrying one VLESS TCP request.
 */
class VlessUpstream private constructor(
    private val profile: Profile,
    private val protect: (Socket) -> Unit,
) : Upstream {
    private val uuid: ByteArray = uuidBytes(profile.vlessUuid)
    private val sni = profile.sni.ifBlank { profile.sshHost }
    private val wsHost = profile.wsHost.ifBlank { sni }
    @Volatile private var closed = false
    private val consecutiveFailures = AtomicInteger(0)

    override val isConnected: Boolean get() = !closed && consecutiveFailures.get() < 6
    override val description: String get() = "VLESS ${profile.sshHost}:${profile.sshPort}"

    override fun open(host: String, port: Int, timeoutMs: Int): StreamChannel {
        if (closed) throw IOException("closed")
        try {
            val ws = connectWebSocket(timeoutMs)
            ws.output.write(requestHeader(host, port))
            consecutiveFailures.set(0)
            return VlessChannel(ws)
        } catch (e: Exception) {
            consecutiveFailures.incrementAndGet()
            throw e
        }
    }

    private fun connectWebSocket(timeoutMs: Int): WebSocket {
        val raw = Socket()
        protect(raw)
        raw.tcpNoDelay = true
        try {
            raw.connect(InetSocketAddress(profile.sshHost, profile.sshPort), timeoutMs)
            val tls = Tls.wrap(raw, sni, profile.sshPort, timeoutMs)
            return WebSocket.connect(tls, wsHost, profile.wsPath, timeoutMs)
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw e
        }
    }

    /** VLESS request: version, uuid, no addons, TCP command, port, address. */
    private fun requestHeader(host: String, port: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0)
        out.write(uuid)
        out.write(0)
        out.write(1)
        out.write(port shr 8); out.write(port and 0xff)
        val v4 = host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))
        when {
            v4 -> { out.write(1); out.write(InetAddress.getByName(host).address) }
            host.contains(':') -> { out.write(3); out.write(InetAddress.getByName(host).address) }
            else -> {
                val b = host.toByteArray(Charsets.US_ASCII)
                out.write(2); out.write(b.size); out.write(b)
            }
        }
        return out.toByteArray()
    }

    override fun close() {
        closed = true
    }

    private class VlessChannel(private val ws: WebSocket) : StreamChannel {
        override val output: OutputStream = ws.output
        override val input: InputStream = object : InputStream() {
            private var headerDone = false
            private fun skipHeader() {
                if (headerDone) return
                val d = DataInputStream(ws.input)
                d.readUnsignedByte() // version
                val addons = d.readUnsignedByte()
                if (addons > 0) d.readFully(ByteArray(addons))
                headerDone = true
            }
            override fun read(): Int {
                skipHeader()
                return ws.input.read()
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                try {
                    skipHeader()
                } catch (e: java.io.EOFException) {
                    return -1
                }
                return ws.input.read(b, off, len)
            }
        }
        override fun close() = ws.close()
    }

    companion object {
        /** Connects and verifies the server with a DNS query to 1.1.1.1 through VLESS. */
        fun connect(
            profile: Profile,
            protect: (Socket) -> Unit,
            probeHost: String = "1.1.1.1",
            probePort: Int = 53,
        ): VlessUpstream {
            if (runCatching { uuidBytes(profile.vlessUuid) }.isFailure) {
                throw IOException("Invalid VLESS UUID \"${profile.vlessUuid}\"")
            }
            val up = VlessUpstream(profile, protect)
            AppLog.i("Connecting to VLESS server ${profile.sshHost}:${profile.sshPort} (TLS SNI ${up.sni}, WS ${up.wsHost}${profile.wsPath})")
            val ch = try {
                up.open(probeHost, probePort, 15_000)
            } catch (e: Exception) {
                throw IOException("VLESS connection failed: ${e.message ?: e.javaClass.simpleName}", e)
            }
            try {
                val query = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0, 0, 2, 0, 1)
                ch.output.write(byteArrayOf(0, query.size.toByte()) + query)
                val din = DataInputStream(ch.input)
                val len = din.readUnsignedShort()
                din.readFully(ByteArray(len))
            } catch (e: Exception) {
                throw IOException("VLESS server did not answer (wrong UUID, path or server down): ${e.message ?: e.javaClass.simpleName}", e)
            } finally {
                ch.close()
            }
            AppLog.ok("VLESS server answered through the tunnel")
            return up
        }

        fun uuidBytes(s: String): ByteArray {
            val u = UUID.fromString(s.trim())
            return java.nio.ByteBuffer.allocate(16).putLong(u.mostSignificantBits).putLong(u.leastSignificantBits).array()
        }
    }
}
