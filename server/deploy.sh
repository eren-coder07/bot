#!/usr/bin/env bash
# One-command deploy for the Jat Vpn control server on a fresh Debian/Ubuntu VPS (run as root).
#
#   APP_SECRET=<same value baked into the app> \
#   ADMIN_PASSWORD=<choose one> \
#   BOOTSTRAP_KEY=<an access key to hand out> \
#   bash deploy.sh
#
# APP_SECRET MUST equal the value compiled into the APK, or the app can't read configs.
set -euo pipefail

: "${APP_SECRET:?set APP_SECRET to the value baked into the app}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(openssl rand -hex 8)}"
COOKIE_SECRET="${COOKIE_SECRET:-$(openssl rand -hex 32)}"
BOOTSTRAP_KEY="${BOOTSTRAP_KEY:-$(tr -dc 'A-HJ-NP-Z2-9' </dev/urandom | head -c12 | sed 's/.\{4\}/&-/g;s/-$//')}"
REPO="${REPO:-https://github.com/eren-coder07/bot}"
BRANCH="${BRANCH:-jat-vpn-opus}"
PORT="${PORT:-8080}"
DIR=/opt/jatvpn

apt-get update -y
apt-get install -y python3-venv git curl

rm -rf "$DIR/repo"
git clone --depth 1 --branch "$BRANCH" "$REPO" "$DIR/repo"
mkdir -p "$DIR/server" /var/lib/jatvpn
cp -r "$DIR/repo/server/." "$DIR/server/"
python3 -m venv "$DIR/venv"
"$DIR/venv/bin/pip" install -q -r "$DIR/server/requirements.txt"

cat > "$DIR/server/.env" <<ENV
APP_SECRET=$APP_SECRET
ADMIN_PASSWORD=$ADMIN_PASSWORD
COOKIE_SECRET=$COOKIE_SECRET
BOOTSTRAP_KEY=$BOOTSTRAP_KEY
DB_PATH=/var/lib/jatvpn/data.db
PORT=$PORT
ENV

cat > /etc/systemd/system/jatvpn.service <<UNIT
[Unit]
Description=Jat Vpn control server
After=network.target
[Service]
WorkingDirectory=$DIR/server
EnvironmentFile=$DIR/server/.env
ExecStart=$DIR/venv/bin/uvicorn app:app --host 0.0.0.0 --port $PORT
Restart=always
RestartSec=3
[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now jatvpn
# open the port if ufw is active
command -v ufw >/dev/null && ufw allow "$PORT"/tcp || true
sleep 2
echo "=============================================="
echo " Jat Vpn server deployed."
echo "  Admin panel : http://$(curl -s ifconfig.me 2>/dev/null || echo YOUR_IP):$PORT/admin"
echo "  Admin pass  : $ADMIN_PASSWORD"
echo "  Access key  : $BOOTSTRAP_KEY   (enter this in the app)"
echo "  Health      : $(curl -s localhost:$PORT/api/v1/ping || echo 'not responding')"
echo "=============================================="
