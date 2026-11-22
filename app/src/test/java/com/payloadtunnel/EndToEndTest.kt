package com.payloadtunnel

import com.payloadtunnel.core.ProxyHandshake
import com.payloadtunnel.core.Socks5Server
import com.payloadtunnel.core.SshConnection
import com.payloadtunnel.core.TunnelStatus
import com.payloadtunnel.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket

/**
 * Full tunnel test without Android: payload proxy -> SSH -> SOCKS5 -> HTTP and DNS.
 * Runs only when E2E_PROXY (host:port of a payload proxy that leads to sshd) is set, e.g.
 *   E2E_PROXY=127.0.0.1:18080 E2E_SSH=127.0.0.1:2222 E2E_USER=konan E2E_PASS=1949 \
 *   E2E_HTTP=127.0.0.1:18081 ./gradlew testReleaseUnitTest
 */
class EndToEndTest {
    private fun env(name: String) = System.getenv(name)

    @Test
    fun tunnelWorksEndToEnd() {
        assumeTrue("E2E_PROXY not set", env("E2E_PROXY") != null)
        val (sshHost, sshPort) = Profile.parseHostPort(env("E2E_SSH"))!!
        val profile = Profile.defaultProfile().copy(
            sshHost = sshHost, sshPort = sshPort, username = env("E2E_USER"), password = env("E2E_PASS"),
            proxy = env("E2E_PROXY"),
        )
        val prepared = ProxyHandshake(profile, "UA-test", {}, {}, {}).open()
        val ssh = SshConnection.connect(profile, prepared)
        assertTrue(ssh.isConnected)

        // Occupy the preferred port to prove the automatic fallback.
        val blocker = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val socks = Socks5Server(ssh)
        val port = socks.start(blocker.localPort)
        assertNotEquals(blocker.localPort, port)

        // HTTP through SOCKS5 CONNECT
        val (httpHost, httpPort) = Profile.parseHostPort(env("E2E_HTTP"))!!
        val s = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
        s.connect(InetSocketAddress.createUnresolved(httpHost, httpPort), 10_000)
        s.getOutputStream().write("GET / HTTP/1.0\r\nHost: $httpHost\r\n\r\n".toByteArray())
        val body = s.getInputStream().readBytes().toString(Charsets.UTF_8)
        assertTrue(body, body.contains("hello-through-tunnel"))
        assertTrue(TunnelStatus.bytesDown.get() > 0 && TunnelStatus.bytesUp.get() > 0)

        // DNS through SOCKS5 UDP ASSOCIATE -> DNS over TCP via SSH
        if (env("E2E_DNS") != null) {
            val ctl = Socket("127.0.0.1", port)
            val o = ctl.getOutputStream()
            val i = DataInputStream(ctl.getInputStream())
            o.write(byteArrayOf(5, 1, 0)); i.readFully(ByteArray(2))
            o.write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
            val rep = ByteArray(10).also { i.readFully(it) }
            assertEquals(0, rep[1].toInt())
            val relayPort = ((rep[8].toInt() and 0xff) shl 8) or (rep[9].toInt() and 0xff)
            val query = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)
            val dnsIp = InetAddress.getByName(env("E2E_DNS")).address
            val pkt = byteArrayOf(0, 0, 0, 1) + dnsIp + byteArrayOf(0, 53) + query
            val udp = DatagramSocket()
            udp.soTimeout = 10_000
            udp.send(DatagramPacket(pkt, pkt.size, InetSocketAddress("127.0.0.1", relayPort)))
            val buf = ByteArray(2048)
            val resp = DatagramPacket(buf, buf.size)
            udp.receive(resp)
            assertEquals(0x12, buf[10].toInt())
            assertEquals(0x34, buf[11].toInt())
            assertTrue((buf[12].toInt() and 0x80) != 0) // QR bit set by the DNS server
            ctl.close()
        }

        socks.stop()
        ssh.close()
        blocker.close()
    }
}
