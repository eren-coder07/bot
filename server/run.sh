#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
[ -f .env ] && set -a && . ./.env && set +a
exec uvicorn app:app --host 0.0.0.0 --port "${PORT:-8080}"
