# Payload Tunnel — SSH tunnel VPN for Android

An Android app (Kotlin, minSdk 24, targetSdk 34) that tunnels all phone traffic through an SSH
server, with HTTP payload injection and remote proxy support (similar to HTTP Custom).

> This repository also contains an unrelated Python bot (`mn.py`, `render.yaml`,
> `requirements.txt`). The Android app lives in `app/` and the Gradle files in the root.

## Download

Every push to `main` builds a signed APK with GitHub Actions and publishes it on the
[Releases page](../../releases/latest). Open the release on your phone, tap the `.apk`
file and allow "Install unknown apps" when Android asks.

## Using the app

1. Open the app. On Android 13+ allow notifications (used for the status notification).
2. **VPN** tab → pick a server card at the bottom (or the **Servers** tab) → tap the power button. The first time, Android asks for VPN
   permission: tap **OK**.
3. The status goes *Connecting → Sending payload → Authenticating → Connected*. The timer,
   upload/download speed and totals are shown on the Home screen.
4. Tap the power button again (or *Disconnect* in the notification) to stop.
   **Speed Test** shows a live speed gauge, ping and totals; **☰** opens Settings.
5. **Logs** shows every step with timestamps (proxy connect, payload sent, server response
   codes, SSH auth, SOCKS start, VPN up). Use the copy button to share a log.

The app ships with one server, **Free Vip**, which bundles several configs. Pick it, then
choose the config in the dropdown under the traffic cards (or under the server on the Servers
screen): WIN\*6 1, WIN\*6 2, IAM\*6 IP 10, IAM\*6 IP 100, IAM\*6 SSH, IAM\*6 SSL (SSH over TLS)
and IAM\*6 VLESS (VLESS over WebSocket + TLS). Every config can be edited. To make your own
group, give several servers the same **Group** name in the editor.

## Adding your own server

**Servers** → **+** (or ⋮ / long-press → *Duplicate* on an existing one and edit it):

| Field | Meaning |
|---|---|
| Profile name | Any name |
| Mode | `SSH Direct` (plain SSH), `SSH-Payload` (payload sent straight to the SSH host), `SSH-Proxy-Payload` (connect to the remote proxy, send payload), `SSH-Proxy (HTTP CONNECT)` (empty payload = `CONNECT host:port`), `SSH-TLS` (SSH inside TLS with a custom SNI), `SSH-TLS-Payload` (TLS, then payload, then SSH), `VLESS-WS-TLS` (VLESS over WebSocket + TLS, as in Xray/v2ray) |
| TLS SNI | Server name sent in TLS (SSH-TLS, VLESS). Certificates are not verified |
| UUID / WebSocket path / Host | VLESS settings |
| Group | Servers with the same group show as one entry with a config dropdown |
| SSH host / port | Your SSH server, e.g. `kn.spedo.qzz.io` / `80` |
| Username / password | SSH account (password is stored encrypted with EncryptedSharedPreferences) |
| Proxy host:port | Remote proxy, e.g. `help.twitter.com:8080`. Empty = connect directly to the SSH host |
| Payload | HTTP payload with tags (below). Real line breaks are ignored, use `[crlf]` |
| Local SOCKS5 port | Default `1080`. If it is busy a free port is chosen automatically |
| Remote DNS | On by default: the SSH server resolves host names (most reliable) |
| DNS servers | Used when Remote DNS is off (default `1.1.1.1, 8.8.8.8`), queried over TCP through SSH |

### Payload tags

| Tag | Replaced by |
|---|---|
| `[crlf]` `[lf]` `[cr]` `[lfcr]` | `\r\n`, `\n`, `\r`, `\n\r` |
| `[host]` `[port]` `[host_port]` / `[ssh]` | SSH host, SSH port, `host:port` |
| `[ua]` | A default mobile Chrome User-Agent |
| `[protocol]` | `HTTP/1.1` |
| `[split]` | Sends the text before it as a separate write, then waits 300 ms |
| `[delay_split]` / `[instant_split]` | Same, with a 1 s delay / no delay |

After sending the payload the app reads and ignores HTTP responses (301, 200, 403, ...) until
the server answers `101 Switching Protocols` or the SSH banner (`SSH-2.0-...`) arrives, then
starts SSH on the same socket. If the server stays silent, SSH starts anyway after 15 s.

### Import / export

Import also accepts decoded NapsternetV-style dumps (`{"source_file", "config": {"configs": [...]}}`
with `sshConfig` or a VLESS `v2rayProfile`) and flat dumps (`ssh_host`, `ssh_port`, `http_proxy`, ...).


**Servers** → top-right ⋮ imports or exports all profiles as JSON; a row's ⋮ → *Export* exports one
profile. Exported files **contain passwords in plain text**, so share them carefully.

Example:

```json
{
  "profiles": [{
    "name": "My server", "mode": "SSH_PROXY_PAYLOAD",
    "sshHost": "ssh.example.com", "sshPort": 80,
    "username": "user", "password": "secret",
    "proxy": "proxy.example.com:8080",
    "payload": "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]",
    "localSocksPort": 1080, "dns": "1.1.1.1, 8.8.8.8"
  }]
}
```

## Settings

- **Auto-reconnect** with a retry limit (`0` = unlimited). While reconnecting the VPN interface
  stays up so traffic does not leak outside the tunnel.
- **Per-app VPN**: all apps, only selected apps, or all apps except selected.

## How it works

```
apps → TUN (VpnService) → hev-socks5-tunnel → SOCKS5 127.0.0.1:1080 → SSH direct-tcpip channels
                                                                          │
     socket → remote proxy ── payload ── 101 / banner ── SSH (JSch) ──────┘
```

- `core/ProxyHandshake.kt`: TCP connect, payload injection, HTTP response handling.
- `core/SshConnection.kt`: JSch session over the prepared socket (no strict host key
  checking, keep-alive every 15 s, password + keyboard-interactive).
- `core/Socks5Server.kt`: SOCKS5 server (dynamic forwarding, like `ssh -D`); UDP DNS is
  converted to DNS-over-TCP through the tunnel.
- `vpn/TunnelVpnService.kt`: foreground VpnService, tun2socks, reconnect logic, notification.

## Building locally

Requirements: JDK 17, Android SDK 34, NDK 26.3.11579264 (Gradle installs missing parts).

```bash
git clone --recursive <this repo>
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Without a keystore the local release build is signed with the debug key. To use your own,
create `keystore.properties` in the root with `storeFile`, `storePassword`, `keyAlias`,
`keyPassword`.

### Signing in GitHub Actions

If these repository secrets exist they are used: `RELEASE_KEYSTORE_BASE64` (base64 of the
`.jks`), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Otherwise the
workflow generates a keystore and keeps it in the Actions cache so later builds install as
updates. GitHub deletes caches unused for 7 days; if that happens the next APK has a new
signature and you must uninstall the old app before installing it. Add the secrets to avoid this.

## Android limitations

- IPv6 is not routed into the tunnel; Android blocks it for tunnelled apps so they use IPv4.
- Only TCP goes through SSH. DNS is resolved by the server (or converted to TCP); other UDP
  (QUIC, games, voice calls) cannot be carried by an SSH tunnel and is dropped, so apps fall
  back to TCP where they can.
- Only one VPN app can be active at a time; starting another VPN disconnects this one.
- Android may show a key icon and "VPN active" notification; this cannot be hidden.
- Payload/proxy tricks depend on your carrier and server. If the server or proxy rejects the
  payload, the Logs screen shows the exact HTTP responses it returned.
