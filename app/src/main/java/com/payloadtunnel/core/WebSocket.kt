package com.payloadtunnel.core

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom

/** Minimal RFC 6455 WebSocket client carrying a binary byte stream. */
class WebSocket private constructor(private val socket: Socket, private val rawIn: InputStream) {
    private val rawOut: OutputStream = socket.getOutputStream()
    private val random = SecureRandom()
    @Volatile private var closed = false

    val input: InputStream = FrameInputStream()
    val output: OutputStream = FrameOutputStream()

    fun close() {
        if (closed) return
        closed = true
        runCatching { sendFrame(0x8, ByteArray(0), 0, 0) }
        runCatching { socket.close() }
    }

    @Synchronized
    private fun sendFrame(opcode: Int, data: ByteArray, off: Int, len: Int) {
        val header = ByteArray(14)
        var h = 0
        header[h++] = (0x80 or opcode).toByte()
        when {
            len < 126 -> header[h++] = (0x80 or len).toByte()
            len < 65536 -> {
                header[h++] = (0x80 or 126).toByte()
                header[h++] = (len shr 8).toByte(); header[h++] = len.toByte()
            }
            else -> {
                header[h++] = (0x80 or 127).toByte()
                for (i in 7 downTo 0) header[h++] = (len.toLong() shr (8 * i)).toByte()
            }
        }
        val mask = ByteArray(4).also { random.nextBytes(it) }
        System.arraycopy(mask, 0, header, h, 4); h += 4
        val payload = ByteArray(len)
        for (i in 0 until len) payload[i] = (data[off + i].toInt() xor mask[i and 3].toInt()).toByte()
        rawOut.write(header, 0, h)
        rawOut.write(payload)
        rawOut.flush()
    }

    private inner class FrameOutputStream : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("WebSocket closed")
            var o = off
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, MAX_FRAME)
                sendFrame(0x2, b, o, n)
                o += n; remaining -= n
            }
        }
        override fun close() = this@WebSocket.close()
    }

    private inner class FrameInputStream : InputStream() {
        private val din = DataInputStream(rawIn)
        private var remaining = 0L
        private var mask: ByteArray? = null
        private var maskPos = 0L
        private var eof = false

        private fun nextDataFrame(): Boolean {
            while (true) {
                val b0 = din.read()
                if (b0 < 0) return false
                val b1 = din.readUnsignedByte()
                val opcode = b0 and 0x0f
                var len = (b1 and 0x7f).toLong()
                if (len == 126L) len = din.readUnsignedShort().toLong()
                else if (len == 127L) len = din.readLong()
                val m = if (b1 and 0x80 != 0) ByteArray(4).also { din.readFully(it) } else null
                when (opcode) {
                    0x0, 0x1, 0x2 -> {
                        if (len == 0L) continue
                        remaining = len; mask = m; maskPos = 0
                        return true
                    }
                    0x8 -> return false // close
                    0x9 -> { // ping -> pong
                        val p = ByteArray(len.toInt()).also { din.readFully(it) }
                        if (m != null) for (i in p.indices) p[i] = (p[i].toInt() xor m[i and 3].toInt()).toByte()
                        runCatching { sendFrame(0xA, p, 0, p.size) }
                    }
                    else -> din.skipBytes(len.toInt())
                }
            }
        }

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (eof) return -1
            if (remaining == 0L && !nextDataFrame()) {
                eof = true
                return -1
            }
            val n = din.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) {
                eof = true
                return -1
            }
            mask?.let { m ->
                for (i in 0 until n) b[off + i] = (b[off + i].toInt() xor m[((maskPos + i) and 3).toInt()].toInt()).toByte()
            }
            maskPos += n
            remaining -= n
            return n
        }

        override fun close() = this@WebSocket.close()
    }

    companion object {
        private const val MAX_FRAME = 16 * 1024
        private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        fun base64(data: ByteArray): String {
            val sb = StringBuilder()
            var i = 0
            while (i < data.size) {
                val b0 = data[i].toInt() and 0xff
                val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xff else -1
                val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xff else -1
                sb.append(B64[b0 shr 2])
                sb.append(B64[((b0 and 3) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
                sb.append(if (b1 < 0) '=' else B64[((b1 and 15) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
                sb.append(if (b2 < 0) '=' else B64[b2 and 63])
                i += 3
            }
            return sb.toString()
        }

        /** Performs the HTTP upgrade on a connected (TLS) socket. */
        fun connect(socket: Socket, host: String, path: String, timeoutMs: Int): WebSocket {
            val key = base64(ByteArray(16).also { SecureRandom().nextBytes(it) })
            val req = "GET ${path.ifBlank { "/" }} HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "User-Agent: ${Payload.DEFAULT_UA}\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
            socket.getOutputStream().apply { write(req.toByteArray(Charsets.ISO_8859_1)); flush() }
            socket.soTimeout = timeoutMs
            val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val c = input.read()
                if (c < 0) throw IOException("Server closed the connection during WebSocket upgrade")
                header.append(c.toChar())
                if (header.length > 16 * 1024) throw IOException("WebSocket response too large")
            }
            val status = header.lineSequence().first().trim()
            if (status.split(' ').getOrNull(1) != "101") {
                throw IOException("WebSocket upgrade refused: $status")
            }
            socket.soTimeout = 0
            return WebSocket(socket, input)
        }
    }
}
