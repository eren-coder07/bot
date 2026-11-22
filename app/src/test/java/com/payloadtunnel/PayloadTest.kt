package com.payloadtunnel

import com.payloadtunnel.core.Payload
import com.payloadtunnel.data.Profile
import com.payloadtunnel.data.TunnelMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadTest {
    private fun text(parts: List<Payload.Part>) = parts.joinToString("|") { String(it.bytes, Charsets.ISO_8859_1) }

    @Test
    fun expandsDefaultPayload() {
        val p = Profile.defaultProfile()
        val parts = Payload.build(p.payload, p.sshHost, p.sshPort, "UA")
        assertEquals(1, parts.size)
        assertEquals(
            "GET / HTTP/1.1\r\nHost: dev-guest-cf.pscp.tv \r\nConnection: Keep-Alive\r\nUser-Agent: UA\r\n\r\n" +
                "BMOVE / HTTP/1.1\r\nHost: kn.spedo.qzz.io\r\nUpgrade: websocket\r\n\r\n",
            text(parts),
        )
    }

    @Test
    fun handlesTagsCaseInsensitiveAndIgnoresRealNewlines() {
        val parts = Payload.build("CONNECT [HOST_PORT] [protocol][CRLF]\nX: [port][lf][cr]", "h.example", 443)
        assertEquals("CONNECT h.example:443 HTTP/1.1\r\nX: 443\n\r", text(parts))
    }

    @Test
    fun splitsPayload() {
        val parts = Payload.build("A[crlf][split]B[instant_split]C[delay_split]D", "h", 22)
        assertEquals("A\r\n|B|C|D", text(parts))
        assertEquals(Payload.SPLIT_DELAY_MS, parts[0].delayAfterMs)
        assertEquals(0L, parts[1].delayAfterMs)
        assertEquals(Payload.LONG_SPLIT_DELAY_MS, parts[2].delayAfterMs)
    }

    @Test
    fun parsesHostPort() {
        assertEquals("help.twitter.com" to 8080, Profile.parseHostPort("help.twitter.com:8080"))
        assertEquals("::1" to 3128, Profile.parseHostPort("[::1]:3128"))
        assertEquals("proxy" to 8080, Profile.parseHostPort("proxy"))
        assertNull(Profile.parseHostPort(""))
        assertNull(Profile.parseHostPort("host:abc"))
    }

    @Test
    fun profileJsonRoundTrip() {
        val p = Profile.defaultProfile()
        val back = Profile.fromJson(JSONObject(p.toJson().toString()))
        assertEquals(p, back)
        assertEquals(TunnelMode.SSH_PROXY_PAYLOAD, TunnelMode.parse("SSH-Proxy-Payload"))
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), p.dnsServers)
    }
}
