package com.payloadtunnel

import com.payloadtunnel.core.ProxyHandshake
import com.payloadtunnel.core.Socks5Server
import com.payloadtunnel.core.SshConnection
import com.payloadtunnel.core.Upstream
import com.payloadtunnel.core.VlessUpstream
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.TunnelMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Opt-in tests against real servers:
 *   E2E_VLESS=127.0.0.1:18443 (Xray VLESS+WS+TLS, path /vless, the built-in UUID)
 *   E2E_SSH_TLS=127.0.0.1:18444 (TLS in front of sshd), E2E_USER / E2E_PASS
 *   E2E_HTTP=127.0.0.1:18081 (serves index.html and big.bin), E2E_PROBE=host:port (TCP DNS)
 */
class VlessTlsEndToEndTest {
    private fun env(n: String): String? = System.getenv(n)

    private fun fetchThrough(up: Upstream) {
        val socks = Socks5Server(up)
        val port = socks.start(0)
        val (h, p) = Profile.parseHostPort(env("E2E_HTTP")!!)!!
        fun get(path: String): ByteArray {
            val s = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            s.soTimeout = 30_000
            s.connect(InetSocketAddress.createUnresolved(h, p), 10_000)
            s.getOutputStream().write("GET /$path HTTP/1.0\r\nHost: $h\r\n\r\n".toByteArray())
            return s.getInputStream().readBytes().also { s.close() }
        }
        val body = get("").toString(Charsets.UTF_8)
        assertTrue(body, body.contains("hello-through-tunnel"))
        val big = get("big.bin").size
        println("big.bin through ${up.description}: $big bytes")
        assertTrue(big > 40_000_000)
        socks.stop()
        up.close()
    }

    @Test
    fun vlessWebSocketTls() {
        assumeTrue(env("E2E_VLESS") != null)
        val (host, port) = Profile.parseHostPort(env("E2E_VLESS")!!)!!
        val (ph, pp) = Profile.parseHostPort(env("E2E_PROBE")!!)!!
        val profile = Profile(
            name = "vless", sshHost = host, sshPort = port, mode = TunnelMode.VLESS_WS_TLS,
            vlessUuid = "3cd6e7b2-bf14-43e1-84a2-ca80a85842b3",
            sni = "bilal1.eyadvip.site", wsHost = "bilal1.eyadvip.site", wsPath = "/vless",
        )
        fetchThrough(VlessUpstream.connect(profile, {}, ph, pp))
    }

    @Test
    fun vlessWrongUuidFailsClearly() {
        assumeTrue(env("E2E_VLESS") != null)
        val (host, port) = Profile.parseHostPort(env("E2E_VLESS")!!)!!
        val (ph, pp) = Profile.parseHostPort(env("E2E_PROBE")!!)!!
        val profile = Profile(
            name = "vless", sshHost = host, sshPort = port, mode = TunnelMode.VLESS_WS_TLS,
            vlessUuid = "00000000-0000-0000-0000-000000000000", sni = "x", wsHost = "x", wsPath = "/vless",
        )
        val err = runCatching { VlessUpstream.connect(profile, {}, ph, pp) }.exceptionOrNull()
        println("wrong uuid -> ${err?.message}")
        assertTrue(err != null)
    }

    @Test
    fun sshOverTlsWithSni() {
        assumeTrue(env("E2E_SSH_TLS") != null)
        val (host, port) = Profile.parseHostPort(env("E2E_SSH_TLS")!!)!!
        val profile = Profile(
            name = "ssl", sshHost = host, sshPort = port, mode = TunnelMode.SSH_TLS, sni = "google.com",
            username = env("E2E_USER")!!, password = env("E2E_PASS")!!,
            proxy = "investor.snap.com:80", payload = "GET / HTTP/1.1[crlf][crlf]", // ignored in SSH-TLS
        )
        val prepared = ProxyHandshake(profile, "UA", {}, {}, {}).open()
        fetchThrough(SshConnection.connect(profile, prepared))
    }
}
