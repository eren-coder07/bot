package com.payloadtunnel.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses profile JSON in several formats:
 *  - this app's export ({"profiles": [...]}, an array, or one profile object)
 *  - decoded NapsternetV-style dumps ({"source_file", "config": {"configs": [{sshConfig | v2rayProfile}]}})
 *  - flat dumps ({"name", "ssh_host", "ssh_port", "ssh_username", "ssh_password", "ssh_mode", "http_proxy", "payload"})
 */
object ConfigImport {

    fun parse(text: String): List<Profile> {
        val t = text.trim()
        val items: List<JSONObject> = when {
            t.startsWith("[") -> JSONArray(t).objects()
            else -> {
                val o = JSONObject(t)
                o.optJSONArray("profiles")?.objects() ?: listOf(o)
            }
        }
        return items.flatMap { parseItem(it) }.filter { it.sshHost.isNotBlank() }
    }

    private fun parseItem(o: JSONObject): List<Profile> {
        val baseName = nameFromSource(o.optString("source_file")) ?: o.optString("name").ifBlank { null }
        o.optJSONObject("config")?.optJSONArray("configs")?.let { configs ->
            val list = configs.objects()
            return list.mapIndexedNotNull { i, c ->
                val name = when {
                    baseName != null && list.size > 1 -> "$baseName ${i + 1}"
                    baseName != null -> baseName
                    else -> c.optString("name", "Imported")
                }
                c.optJSONObject("sshConfig")?.let { fromNpvSsh(it, name) }
                    ?: c.optJSONObject("v2rayProfile")?.let { fromV2ray(it, name) }
            }
        }
        if (o.has("ssh_host")) {
            return listOf(
                Profile(
                    name = baseName ?: "Imported",
                    sshHost = o.optString("ssh_host"),
                    sshPort = o.optString("ssh_port").toIntOrNull() ?: 22,
                    username = o.optString("ssh_username"),
                    password = o.optString("ssh_password"),
                    mode = TunnelMode.parse(if (o.has("ssh_mode")) o.optString("ssh_mode") else null),
                    proxy = o.optString("http_proxy"),
                    payload = o.optString("payload"),
                    sni = o.optString("sni"),
                ),
            )
        }
        return listOf(Profile.fromJson(o))
    }

    private fun fromNpvSsh(s: JSONObject, name: String) = Profile(
        name = name,
        sshHost = s.optString("sshHost"),
        sshPort = s.optString("sshPort").toIntOrNull() ?: 22,
        username = s.optString("sshUsername"),
        password = s.optString("sshPassword"),
        mode = TunnelMode.parse(s.optString("sshConfigType").ifBlank { "SSH-Proxy-Payload" }),
        proxy = s.optString("httpProxy"),
        payload = s.optString("payload"),
        sni = s.optString("sni"),
    )

    private fun fromV2ray(v: JSONObject, name: String): Profile? {
        val outbound = v.optJSONObject("v2rayJson")?.optJSONArray("outbounds")?.objects()
            ?.firstOrNull { it.optString("protocol") == "vless" } ?: return null
        val vnext = outbound.optJSONObject("settings")?.optJSONArray("vnext")?.optJSONObject(0) ?: return null
        val user = vnext.optJSONArray("users")?.optJSONObject(0) ?: return null
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
        if (stream.optString("network", "ws") != "ws") return null // only VLESS over WebSocket is supported
        val tls = stream.optJSONObject("tlsSettings") ?: JSONObject()
        val ws = stream.optJSONObject("wsSettings") ?: JSONObject()
        val host = vnext.optString("address")
        return Profile(
            name = name,
            sshHost = host,
            sshPort = vnext.optString("port").toIntOrNull() ?: 443,
            mode = TunnelMode.VLESS_WS_TLS,
            vlessUuid = user.optString("id"),
            sni = tls.optString("serverName"),
            wsHost = ws.optJSONObject("headers")?.optString("Host").orEmpty(),
            wsPath = ws.optString("path", "/").ifEmpty { "/" },
        )
    }

    /** "c4333ce7-lAM__6_VPS_SSH__1.npvs" -> "lAM 6 VPS SSH 1" */
    fun nameFromSource(source: String): String? {
        if (source.isBlank()) return null
        var n = source.substringAfterLast('/').removeSuffix(".npvs").removeSuffix(".json")
        n = n.replace(Regex("^[0-9a-fA-F]{8}-"), "")
        return n.replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifEmpty { null }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
