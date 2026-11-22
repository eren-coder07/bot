package com.payloadtunnel

import com.payloadtunnel.core.Socks5Server
import com.payloadtunnel.core.StreamChannel
import com.payloadtunnel.core.Upstream
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** The share proxy must serve BOTH HTTP and SOCKS5 (PCs usually use an HTTP proxy). */
class ShareProxyTest {
    private val lo = InetAddress.getByName("127.0.0.1")

    private class DirectUpstream : Upstream {
        override val isConnected = true
        override val description = "direct"
        override fun open(host: String, port: Int, timeoutMs: Int): StreamChannel {
            val s = Socket(); s.connect(InetSocketAddress(host, port), timeoutMs)
            return object : StreamChannel {
                override val input: InputStream = s.getInputStream()
                override val output: OutputStream = s.getOutputStream()
                override fun close() { s.close() }
            }
        }
        override fun close() {}
    }

    private fun originServer(): ServerSocket {
        val srv = ServerSocket(0, 50, lo)
        thread(isDaemon = true) {
            while (!srv.isClosed) {
                val c = runCatching { srv.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        val inp = c.getInputStream()
                        val buf = StringBuilder()
                        while (!buf.endsWith("\r\n\r\n")) { val b = inp.read(); if (b < 0) break; buf.append(b.toChar()) }
                        c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello".toByteArray())
                        c.getOutputStream().flush()
                        c.close()
                    }
                }
            }
        }
        return srv
    }

    @Test fun httpAbsoluteUri() {
        val origin = originServer()
        val proxy = Socks5Server(DirectUpstream(), lo, allowHttp = true)
        val pport = proxy.start(0)
        val s = Socket(); s.soTimeout = 8000; s.connect(InetSocketAddress("127.0.0.1", pport), 5000)
        s.getOutputStream().write(
            ("GET http://127.0.0.1:${origin.localPort}/ HTTP/1.1\r\nHost: 127.0.0.1\r\nProxy-Connection: keep-alive\r\n\r\n").toByteArray(),
        )
        val resp = s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("resp=$resp", resp.contains("hello"))
        s.close(); proxy.stop(); origin.close()
    }

    @Test fun httpConnectTunnel() {
        // echo upstream
        val echo = ServerSocket(0, 50, lo)
        thread(isDaemon = true) {
            val c = runCatching { echo.accept() }.getOrNull() ?: return@thread
            val b = ByteArray(4); val n = c.getInputStream().read(b)
            if (n > 0) { c.getOutputStream().write(b, 0, n); c.getOutputStream().flush() }
        }
        val proxy = Socks5Server(DirectUpstream(), lo, allowHttp = true)
        val pport = proxy.start(0)
        val s = Socket(); s.soTimeout = 8000; s.connect(InetSocketAddress("127.0.0.1", pport), 5000)
        s.getOutputStream().write("CONNECT 127.0.0.1:${echo.localPort} HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
        val rl = s.getInputStream().bufferedReader().readLine()
        assertTrue("status=$rl", rl.contains("200"))
        // after 200, read the blank line then tunnel
        s.getOutputStream().write("ping".toByteArray()); s.getOutputStream().flush()
        val echoed = ByteArray(4); var got = 0
        while (got < 4) { val r = s.getInputStream().read(echoed, got, 4 - got); if (r < 0) break; got += r }
        assertTrue("echoed=${String(echoed)}", String(echoed, 0, got).contains("ping"))
        s.close(); proxy.stop(); echo.close()
    }

    @Test fun stillSpeaksSocks5() {
        val origin = originServer()
        val proxy = Socks5Server(DirectUpstream(), lo, allowHttp = true)
        val pport = proxy.start(0)
        val s = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", pport)))
        s.soTimeout = 8000
        s.connect(InetSocketAddress.createUnresolved("127.0.0.1", origin.localPort), 5000)
        s.getOutputStream().write("GET / HTTP/1.0\r\nHost: x\r\n\r\n".toByteArray())
        val resp = s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("resp=$resp", resp.contains("hello"))
        s.close(); proxy.stop(); origin.close()
    }
}
