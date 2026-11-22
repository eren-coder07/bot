package com.payloadtunnel.data

import org.json.JSONObject
import java.util.UUID

enum class TunnelMode(
    val label: String,
    val usesProxy: Boolean,
    val usesPayload: Boolean,
    val usesTls: Boolean = false,
    val isVless: Boolean = false,
) {
    SSH_DIRECT("SSH Direct", false, false),
    SSH_PAYLOAD("SSH-Payload", false, true),
    SSH_PROXY_PAYLOAD("SSH-Proxy-Payload", true, true),
    SSH_PROXY("SSH-Proxy (HTTP CONNECT)", true, true),
    SSH_TLS("SSH-TLS", false, false, usesTls = true),
    SSH_TLS_PAYLOAD("SSH-TLS-Payload", false, true, usesTls = true),
    VLESS_WS_TLS("VLESS-WS-TLS", false, false, usesTls = true, isVless = true);

    val isSsh: Boolean get() = !isVless

    companion object {
        fun parse(value: String?): TunnelMode {
            if (value == null) return SSH_PROXY_PAYLOAD
            val norm = value.uppercase().replace('-', '_').replace(' ', '_')
            if (norm == "SSH_SSL" || norm == "SSH_TLS") return SSH_TLS
            if (norm.startsWith("VLESS")) return VLESS_WS_TLS
            return entries.firstOrNull { it.name == norm }
                ?: entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
                ?: SSH_PROXY_PAYLOAD
        }
    }
}

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val sshHost: String = "",
    val sshPort: Int = 22,
    val username: String = "",
    val password: String = "",
    val mode: TunnelMode = TunnelMode.SSH_PROXY_PAYLOAD,
    /** Remote HTTP proxy as "host:port". Empty means connect straight to the SSH host. */
    val proxy: String = "",
    val payload: String = "",
    val localSocksPort: Int = 1080,
    /** Comma / space separated list of DNS server IPs (used when [remoteDns] is off). */
    val dns: String = DEFAULT_DNS,
    /** Servers with the same group are shown as one entry with a variant dropdown. */
    val group: String = "",
    /** TLS server name (SNI) for SSH-TLS and VLESS; empty = the host name. */
    val sni: String = "",
    /** VLESS user id (UUID). */
    val vlessUuid: String = "",
    /** WebSocket path and Host header for VLESS-WS. */
    val wsPath: String = "/",
    val wsHost: String = "",
    /**
     * Let the SSH server resolve host names (like "ssh -D" with remote DNS). The phone gets
     * placeholder IPs from the built-in DNS and the real host name is sent in each SOCKS request.
     */
    val remoteDns: Boolean = true,
) {
    val proxyHost: String?
        get() = parseHostPort(proxy)?.first

    val proxyPort: Int?
        get() = parseHostPort(proxy)?.second

    val dnsServers: List<String>
        get() = dns.split(',', ' ', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun toJson(includePassword: Boolean = true): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("sshHost", sshHost)
        put("sshPort", sshPort)
        put("username", username)
        if (includePassword) put("password", password)
        put("mode", mode.name)
        put("proxy", proxy)
        put("payload", payload)
        put("localSocksPort", localSocksPort)
        put("dns", dns)
        put("remoteDns", remoteDns)
        if (group.isNotEmpty()) put("group", group)
        if (sni.isNotEmpty()) put("sni", sni)
        if (vlessUuid.isNotEmpty()) put("vlessUuid", vlessUuid)
        if (mode.isVless) {
            put("wsPath", wsPath)
            put("wsHost", wsHost)
        }
    }

    companion object {
        const val DEFAULT_DNS = "1.1.1.1, 8.8.8.8"

        fun fromJson(o: JSONObject, newId: Boolean = false): Profile {
            // Accept both our own format and a few common alternative key names.
            val proxy = o.optString("proxy").ifEmpty {
                val h = o.optString("proxyHost")
                val p = o.optInt("proxyPort", 0)
                if (h.isNotEmpty() && p > 0) "$h:$p" else h
            }
            return Profile(
                id = if (newId || !o.has("id")) UUID.randomUUID().toString() else o.getString("id"),
                name = o.optString("name", "Imported"),
                sshHost = o.optString("sshHost", o.optString("host")),
                sshPort = o.optInt("sshPort", o.optInt("port", 22)),
                username = o.optString("username", o.optString("user")),
                password = o.optString("password"),
                mode = TunnelMode.parse(if (o.has("mode")) o.optString("mode") else null),
                proxy = proxy,
                payload = o.optString("payload"),
                localSocksPort = o.optInt("localSocksPort", 1080),
                dns = o.optString("dns", DEFAULT_DNS).ifBlank { DEFAULT_DNS },
                remoteDns = o.optBoolean("remoteDns", true),
                group = o.optString("group"),
                sni = o.optString("sni"),
                vlessUuid = o.optString("vlessUuid"),
                wsPath = o.optString("wsPath", "/").ifEmpty { "/" },
                wsHost = o.optString("wsHost"),
            )
        }

        fun parseHostPort(value: String): Pair<String, Int>? {
            val v = value.trim()
            if (v.isEmpty()) return null
            // [ipv6]:port
            if (v.startsWith("[")) {
                val end = v.indexOf(']')
                if (end < 0) return null
                val host = v.substring(1, end)
                val port = v.substring(end + 1).removePrefix(":").toIntOrNull() ?: 8080
                return host to port
            }
            val idx = v.lastIndexOf(':')
            if (idx < 0 || v.count { it == ':' } > 1) return v to 8080
            val port = v.substring(idx + 1).trim().toIntOrNull() ?: return null
            return v.substring(0, idx).trim() to port
        }

        fun defaultProfile() = Profile(
            name = "rifi tech win*6",
            sshHost = "kn.spedo.qzz.io",
            sshPort = 80,
            username = "konan",
            password = "1949",
            mode = TunnelMode.SSH_PROXY_PAYLOAD,
            proxy = "help.twitter.com:8080",
            payload = "GET / HTTP/1.1[crlf]Host: dev-guest-cf.pscp.tv [crlf]Connection: Keep-Alive[crlf]" +
                "User-Agent: [ua][crlf][crlf]BMOVE / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]",
            localSocksPort = 1080,
            dns = DEFAULT_DNS,
        )
    }
}
