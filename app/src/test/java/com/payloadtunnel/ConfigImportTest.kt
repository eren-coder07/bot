package com.payloadtunnel

import com.payloadtunnel.data.BuiltinServers
import com.payloadtunnel.data.ConfigImport
import com.payloadtunnel.data.TunnelMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigImportTest {
    private val npvDump = """
    [
      {"source_file": "c4333ce7-lAM__6_VPS_SSH__1.npvs",
       "config": {"configs": [{"name": "Ssh", "type": "SSH", "sshConfig": {
          "sshConfigType": "SSH-Proxy-Payload", "sshHost": "bilal1.eyadvip.site", "sshPort": "443",
          "sshUsername": "UTREo", "sshPassword": "udh", "httpProxy": "investor.snap.com:80",
          "payload": "GET /cdn-cgi HTTP/1.1 [lf]Host:dev-guest-cf.pscp.tv[lf][lf]"}}]}},
      {"source_file": "d38e5801-lAM__6_VPS_SSL__1.npvs",
       "config": {"configs": [{"name": "Ssh", "type": "SSH", "sshConfig": {
          "sshConfigType": "SSH-TLS", "sshHost": "85.190.101.111", "sshPort": "443",
          "sshUsername": "UTREo", "sshPassword": "udh", "sni": "google.com"}}]}},
      {"source_file": "7dfd4830-lAM__6_VPS_TORJN__1.npvs",
       "config": {"configs": [{"name": "VLESS", "v2rayProfile": {"v2rayJson": {"outbounds": [{
          "protocol": "vless",
          "settings": {"vnext": [{"address": "104.18.37.127", "port": "443",
             "users": [{"encryption": "none", "id": "3cd6e7b2-bf14-43e1-84a2-ca80a85842b3"}]}]},
          "streamSettings": {"network": "ws", "security": "tls",
             "tlsSettings": {"allowInsecure": "true", "serverName": "bilal1.eyadvip.site"},
             "wsSettings": {"headers": {"Host": "bilal1.eyadvip.site"}, "path": "/vless"}}}]}}}]}}
    ]
    """

    private val flatDump = """
    [{"source_file": "27ab407b-rifi_tech_iam_6_ip10.npvs", "name": "rifi tech iam*6 ip10",
      "ssh_host": "kn.spedo.qzz.io", "ssh_port": "80", "ssh_username": "konan", "ssh_password": "1949",
      "ssh_mode": "SSH-Proxy-Payload", "http_proxy": "investor.snap.com:80", "payload": "GET / HTTP/1.1[crlf][crlf]"}]
    """

    @Test
    fun importsNapsternetStyleDump() {
        val list = ConfigImport.parse(npvDump)
        assertEquals(3, list.size)
        val ssh = list[0]
        assertEquals("lAM 6 VPS SSH 1", ssh.name)
        assertEquals(TunnelMode.SSH_PROXY_PAYLOAD, ssh.mode)
        assertEquals("bilal1.eyadvip.site" to 443, ssh.sshHost to ssh.sshPort)
        assertEquals("investor.snap.com:80", ssh.proxy)
        val ssl = list[1]
        assertEquals(TunnelMode.SSH_TLS, ssl.mode)
        assertEquals("google.com", ssl.sni)
        val vless = list[2]
        assertEquals(TunnelMode.VLESS_WS_TLS, vless.mode)
        assertEquals("104.18.37.127" to 443, vless.sshHost to vless.sshPort)
        assertEquals("3cd6e7b2-bf14-43e1-84a2-ca80a85842b3", vless.vlessUuid)
        assertEquals("bilal1.eyadvip.site", vless.sni)
        assertEquals("bilal1.eyadvip.site", vless.wsHost)
        assertEquals("/vless", vless.wsPath)
    }

    @Test
    fun importsFlatDump() {
        val p = ConfigImport.parse(flatDump).single()
        assertEquals("rifi tech iam 6 ip10", p.name)
        assertEquals("kn.spedo.qzz.io" to 80, p.sshHost to p.sshPort)
        assertEquals("konan" to "1949", p.username to p.password)
        assertEquals("investor.snap.com:80", p.proxy)
    }

    @Test
    fun builtinsAreOneGroupWithUniqueIds() {
        val all = BuiltinServers.all
        assertEquals(setOf(BuiltinServers.GROUP), all.map { it.group }.toSet())
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals("WIN*6 1", all.first().name)
    }
}
