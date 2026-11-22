#!/usr/bin/env bash
# Runs ON the VPS (uploaded by the deploy workflow). Reads secrets from the environment.
set -euo pipefail
: "${APP_SECRET:?APP_SECRET is required}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(openssl rand -hex 8)}"
COOKIE_SECRET="${COOKIE_SECRET:-$(openssl rand -hex 32)}"
BOOTSTRAP_KEY="${BOOTSTRAP_KEY:-}"
PORT="${PORT:-8080}"
export DEBIAN_FRONTEND=noninteractive

apt-get update -y
apt-get install -y python3-venv python3-pip curl openssl
mkdir -p /var/lib/jatvpn
python3 -m venv /opt/jatvpn/venv
/opt/jatvpn/venv/bin/pip install -q -r /opt/jatvpn/server/requirements.txt

cat > /opt/jatvpn/server/.env <<ENV
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
WorkingDirectory=/opt/jatvpn/server
EnvironmentFile=/opt/jatvpn/server/.env
ExecStart=/opt/jatvpn/venv/bin/uvicorn app:app --host 0.0.0.0 --port $PORT
Restart=always
RestartSec=3
[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable jatvpn
systemctl restart jatvpn
command -v ufw >/dev/null && ufw allow "$PORT"/tcp >/dev/null 2>&1 || true
sleep 3
echo "HEALTH=$(curl -s --max-time 10 localhost:$PORT/api/v1/ping || echo FAILED)"
echo "ADMIN_URL=http://$(curl -s --max-time 8 ifconfig.me || echo YOUR_IP):$PORT/admin"
echo "DEPLOY_OK"
