# Payload Tunnel — control server & admin panel

FastAPI + SQLite backend that holds all VPN configs server-side, controls access by key,
tracks time credit (1 hour free, +1 hour per watched Start.io ad), and serves a web admin panel.
The Android app ships with **no configs inside it** — it fetches them from here, encrypted.

## What it does
- **Configs live only here.** The app downloads them over TLS, AES-256-GCM encrypted with a
  secret derived from your `APP_SECRET` + the user's access key. A captured response is useless
  without both, and the app never shows or exports them.
- **Access keys.** You generate keys in the admin panel and hand them out. Ban a key and it
  stops working within seconds.
- **Time credit.** Each key starts with 60 minutes. The app counts down; when it hits 0 the
  user watches a rewarded ad to add another 60. The server is the source of truth (wall-clock),
  so a user can't get free time by changing the device clock.
- **Admin panel** at `/admin`: add/edit/delete configs live, import NapsternetV/JSON dumps,
  generate/ban/top-up keys, and change the timing/ad settings without redeploying.

## Deploy on your VPS

```bash
sudo mkdir -p /opt/payloadtunnel /var/lib/payloadtunnel
sudo chown -R $USER /opt/payloadtunnel /var/lib/payloadtunnel
git clone <this repo> /opt/payloadtunnel/repo
cp -r /opt/payloadtunnel/repo/server /opt/payloadtunnel/server
cd /opt/payloadtunnel/server
python3 -m venv /opt/payloadtunnel/venv
/opt/payloadtunnel/venv/bin/pip install -r requirements.txt

cp .env.example .env
# Edit .env — set APP_SECRET (openssl rand -hex 32), ADMIN_PASSWORD, COOKIE_SECRET.
# Put the SAME APP_SECRET in the app build (GitHub secret APP_SECRET).

sudo cp payloadtunnel.service /etc/systemd/system/
sudo systemctl enable --now payloadtunnel
```

Or with Docker:

```bash
docker build -t payloadtunnel ./server
docker run -d --name payloadtunnel -p 8080:8080 \
  -e APP_SECRET=... -e ADMIN_PASSWORD=... -e COOKIE_SECRET=... \
  -v /var/lib/payloadtunnel:/data payloadtunnel
```

### Put it behind HTTPS (strongly recommended)
Use a domain + Caddy or Nginx so the app talks to `https://your-domain`. Example Caddyfile:

```
vpn.yourdomain.com {
    reverse_proxy 127.0.0.1:8080
}
```

Then set that URL as the app's `SERVER_URL` build secret. Plain `http://IP` works but the
configs are then only as private as the network path — use HTTPS.

## Connect the app
In the GitHub Actions build of the Android app set these repository **secrets**:
- `SERVER_URL` — e.g. `https://vpn.yourdomain.com`
- `APP_SECRET` — the exact value from `.env`
- (optional) `CERT_PIN_SHA256` — base64 SHA-256 of your TLS cert's public key, for pinning.

## Admin panel
Open `https://your-domain/admin`, log in with `ADMIN_PASSWORD`.
- **Configs**: the Free Vip set is preloaded. Add/edit/delete; "Import" accepts the files you
  already have (NapsternetV `.npvs` JSON, this app's export, or flat dumps).
- **Access keys**: generate in bulk, set minutes, ban/unban, add time, delete.
- **Settings**: free minutes, minutes per ad, ad limits, Start.io app id, force-update version,
  one-device-per-key, and an announcement shown in the app.

## API (used by the app)
`GET /api/v1/ping`, `POST /api/v1/auth`, `GET /api/v1/configs` (encrypted),
`POST /api/v1/session/heartbeat`, `POST /api/v1/ad/reward` — all except ping need header
`X-Api-Key: <access key>`.

## Honest limits
Configs are kept out of the app and the UI, encrypted in transit, and gated behind a key you
control. But a user who **roots their own phone** can still observe the live tunnel traffic as it
leaves the device — no VPN app can prevent that. What this stops is: configs in the APK file,
configs shown in the UI, config import/export, and plain network capture without your secrets.
