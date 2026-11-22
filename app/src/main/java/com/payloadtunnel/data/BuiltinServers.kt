package com.payloadtunnel.data

/** Servers shipped with the app, shown as one "Free Vip" entry with a variant dropdown. */
object BuiltinServers {
    const val GROUP = "Free Vip"
    /** Bump when the list changes so existing installs get the update. */
    const val VERSION = 1

    private const val PAYLOAD_BMOVE =
        "GET / HTTP/1.1[crlf]Host: dev-guest-cf.pscp.tv [crlf]Connection: Keep-Alive[crlf]User-Agent: [ua][crlf][crlf]" +
            "BMOVE / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]"
    private const val PAYLOAD_CDN =
        "GET /cdn-cgi HTTP/1.1 [lf]Host:dev-guest-cf.pscp.tv[lf][lf]ACL / HTTP/1.1 [lf]Host: [host] [crlf]Connection:[lf]Upgrade: Websocket[lf][lf]"

    val all: List<Profile> = listOf(
        Profile(
            id = "freevip-win6-1", group = GROUP, name = "WIN*6 1",
            sshHost = "kn.spedo.qzz.io", sshPort = 80, username = "konan", password = "1949",
            mode = TunnelMode.SSH_PROXY_PAYLOAD, proxy = "help.twitter.com:8080", payload = PAYLOAD_BMOVE,
        ),
        Profile(
            id = "freevip-win6-2", group = GROUP, name = "WIN*6 2",
            sshHost = "bilal1.eyadvip.site", sshPort = 443, username = "UTREo", password = "udh",
            mode = TunnelMode.SSH_PROXY_PAYLOAD, proxy = "guest-cf.pscp.tv:8080", payload = PAYLOAD_CDN,
        ),
        Profile(
            id = "freevip-iam6-ip10", group = GROUP, name = "IAM*6 IP 10",
            sshHost = "kn.spedo.qzz.io", sshPort = 80, username = "konan", password = "1949",
            mode = TunnelMode.SSH_PROXY_PAYLOAD, proxy = "investor.snap.com:80", payload = PAYLOAD_CDN,
        ),
        Profile(
            id = "freevip-iam6-ip100", group = GROUP, name = "IAM*6 IP 100",
            sshHost = "kn.spedo.qzz.io", sshPort = 80, username = "konan", password = "1949",
            mode = TunnelMode.SSH_PROXY_PAYLOAD, proxy = "172.66.0.227:80", payload = PAYLOAD_BMOVE,
        ),
        Profile(
            id = "freevip-iam6-ssh", group = GROUP, name = "IAM*6 SSH",
            sshHost = "bilal1.eyadvip.site", sshPort = 443, username = "UTREo", password = "udh",
            mode = TunnelMode.SSH_PROXY_PAYLOAD, proxy = "investor.snap.com:80", payload = PAYLOAD_CDN,
        ),
        Profile(
            id = "freevip-iam6-ssl", group = GROUP, name = "IAM*6 SSL",
            sshHost = "85.190.101.111", sshPort = 443, username = "UTREo", password = "udh",
            mode = TunnelMode.SSH_TLS, sni = "google.com",
            proxy = "investor.snap.com:80", payload = PAYLOAD_CDN,
        ),
        Profile(
            id = "freevip-iam6-vless", group = GROUP, name = "IAM*6 VLESS",
            sshHost = "104.18.37.127", sshPort = 443, mode = TunnelMode.VLESS_WS_TLS,
            vlessUuid = "3cd6e7b2-bf14-43e1-84a2-ca80a85842b3",
            sni = "bilal1.eyadvip.site", wsHost = "bilal1.eyadvip.site", wsPath = "/vless",
        ),
    )
}
