"""Parse NapsternetV-style and flat config dumps into config records (server shape)."""
import json
import re
import uuid


def _name_from_source(src: str):
    if not src:
        return None
    n = src.split("/")[-1]
    for suf in (".npvs", ".json"):
        if n.endswith(suf):
            n = n[: -len(suf)]
    n = re.sub(r"^[0-9a-fA-F]{8}-", "", n)
    n = re.sub(r"\s+", " ", n.replace("_", " ")).strip()
    return n or None


def _mode(value: str) -> str:
    if not value:
        return "SSH_PROXY_PAYLOAD"
    norm = value.upper().replace("-", "_").replace(" ", "_")
    if norm in ("SSH_SSL", "SSH_TLS"):
        return "SSH_TLS"
    if norm.startswith("VLESS"):
        return "VLESS_WS_TLS"
    if norm in ("SSH_DIRECT", "SSH_PAYLOAD", "SSH_PROXY_PAYLOAD", "SSH_PROXY",
                "SSH_TLS_PAYLOAD", "VLESS_WS_TLS"):
        return norm
    return "SSH_PROXY_PAYLOAD"


def _blank_config():
    return {
        "name": "Imported", "group": "", "mode": "SSH_PROXY_PAYLOAD",
        "sshHost": "", "sshPort": 22, "username": "", "password": "",
        "proxy": "", "payload": "", "sni": "", "vlessUuid": "",
        "wsPath": "/", "wsHost": "", "dns": "1.1.1.1, 8.8.8.8",
        "remoteDns": True, "localSocksPort": 1080,
    }


def _from_npv_ssh(s: dict, name: str) -> dict:
    c = _blank_config()
    c.update(
        name=name, mode=_mode(s.get("sshConfigType") or "SSH-Proxy-Payload"),
        sshHost=s.get("sshHost", ""), sshPort=int(str(s.get("sshPort", "22")) or 22),
        username=s.get("sshUsername", ""), password=s.get("sshPassword", ""),
        proxy=s.get("httpProxy", ""), payload=s.get("payload", ""), sni=s.get("sni", ""),
    )
    return c


def _from_v2ray(v: dict, name: str):
    outbounds = (v.get("v2rayJson") or {}).get("outbounds") or []
    ob = next((o for o in outbounds if o.get("protocol") == "vless"), None)
    if not ob:
        return None
    vnext = ((ob.get("settings") or {}).get("vnext") or [{}])[0]
    users = vnext.get("users") or [{}]
    stream = ob.get("streamSettings") or {}
    if stream.get("network", "ws") != "ws":
        return None
    tls = stream.get("tlsSettings") or {}
    ws = stream.get("wsSettings") or {}
    c = _blank_config()
    c.update(
        name=name, mode="VLESS_WS_TLS", sshHost=vnext.get("address", ""),
        sshPort=int(str(vnext.get("port", "443")) or 443),
        vlessUuid=users[0].get("id", ""), sni=tls.get("serverName", ""),
        wsHost=(ws.get("headers") or {}).get("Host", ""),
        wsPath=ws.get("path", "/") or "/",
    )
    return c


def parse(text: str) -> list:
    text = text.strip()
    if text.startswith("["):
        items = json.loads(text)
    else:
        obj = json.loads(text)
        items = obj.get("profiles") or [obj]
    out = []
    for o in items:
        if not isinstance(o, dict):
            continue
        base = _name_from_source(o.get("source_file", "")) or o.get("name") or None
        configs = (o.get("config") or {}).get("configs")
        if isinstance(configs, list):
            for i, c in enumerate(configs):
                nm = (f"{base} {i + 1}" if base and len(configs) > 1 else base) or c.get("name", "Imported")
                if c.get("sshConfig"):
                    out.append(_from_npv_ssh(c["sshConfig"], nm))
                elif c.get("v2rayProfile"):
                    r = _from_v2ray(c["v2rayProfile"], nm)
                    if r:
                        out.append(r)
            continue
        if "ssh_host" in o:
            c = _blank_config()
            c.update(
                name=base or "Imported", sshHost=o.get("ssh_host", ""),
                sshPort=int(str(o.get("ssh_port", "22")) or 22),
                username=o.get("ssh_username", ""), password=o.get("ssh_password", ""),
                mode=_mode(o.get("ssh_mode", "")), proxy=o.get("http_proxy", ""),
                payload=o.get("payload", ""), sni=o.get("sni", ""),
            )
            out.append(c)
    return [c for c in out if c.get("sshHost")]
