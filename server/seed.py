"""The built-in configs preloaded on first run (your Free Vip set)."""
PAYLOAD_BMOVE = ("GET / HTTP/1.1[crlf]Host: dev-guest-cf.pscp.tv [crlf]Connection: Keep-Alive[crlf]"
                 "User-Agent: [ua][crlf][crlf]BMOVE / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]")
PAYLOAD_CDN = ("GET /cdn-cgi HTTP/1.1 [lf]Host:dev-guest-cf.pscp.tv[lf][lf]ACL / HTTP/1.1 [lf]"
               "Host: [host] [crlf]Connection:[lf]Upgrade: Websocket[lf][lf]")

SEED = [
    {"name": "WIN*6 1", "group": "Free Vip", "mode": "SSH_PROXY_PAYLOAD",
     "sshHost": "kn.spedo.qzz.io", "sshPort": 80, "username": "konan", "password": "1949",
     "proxy": "help.twitter.com:8080", "payload": PAYLOAD_BMOVE},
    {"name": "WIN*6 2", "group": "Free Vip", "mode": "SSH_PROXY_PAYLOAD",
     "sshHost": "bilal1.eyadvip.site", "sshPort": 443, "username": "UTREo", "password": "udh",
     "proxy": "guest-cf.pscp.tv:8080", "payload": PAYLOAD_CDN},
    {"name": "IAM*6 IP 10", "group": "Free Vip", "mode": "SSH_PROXY_PAYLOAD",
     "sshHost": "kn.spedo.qzz.io", "sshPort": 80, "username": "konan", "password": "1949",
     "proxy": "investor.snap.com:80", "payload": PAYLOAD_CDN},
    {"name": "IAM*6 IP 100", "group": "Free Vip", "mode": "SSH_PROXY_PAYLOAD",
     "sshHost": "kn.spedo.qzz.io", "sshPort": 80, "username": "konan", "password": "1949",
     "proxy": "172.66.0.227:80", "payload": PAYLOAD_BMOVE},
    {"name": "IAM*6 SSH", "group": "Free Vip", "mode": "SSH_PROXY_PAYLOAD",
     "sshHost": "bilal1.eyadvip.site", "sshPort": 443, "username": "UTREo", "password": "udh",
     "proxy": "investor.snap.com:80", "payload": PAYLOAD_CDN},
    {"name": "IAM*6 SSL", "group": "Free Vip", "mode": "SSH_TLS",
     "sshHost": "85.190.101.111", "sshPort": 443, "username": "UTREo", "password": "udh",
     "sni": "google.com", "proxy": "investor.snap.com:80", "payload": PAYLOAD_CDN},
    {"name": "IAM*6 VLESS", "group": "Free Vip", "mode": "VLESS_WS_TLS",
     "sshHost": "104.18.37.127", "sshPort": 443, "vlessUuid": "3cd6e7b2-bf14-43e1-84a2-ca80a85842b3",
     "sni": "bilal1.eyadvip.site", "wsHost": "bilal1.eyadvip.site", "wsPath": "/vless"},
]
