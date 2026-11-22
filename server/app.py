"""Payload Tunnel control server: client API + admin panel (FastAPI + SQLite)."""
import hashlib
import hmac
import json
import os
import secrets
import time

from fastapi import FastAPI, Request, Response, HTTPException, Header
from fastapi.responses import HTMLResponse, JSONResponse, PlainTextResponse

import db
from crypto_box import encrypt
from npv_import import parse as parse_import
from seed import SEED

APP_SECRET = os.environ.get("APP_SECRET", "change-me-shared-app-secret")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "admin")
COOKIE_SECRET = os.environ.get("COOKIE_SECRET", secrets.token_hex(16))
APP_DIR = os.path.dirname(__file__)

app = FastAPI(title="Payload Tunnel Server", docs_url=None, redoc_url=None, openapi_url=None)
db.init(seed_configs=SEED)

# Optional: create a ready-to-use access key on first boot (set BOOTSTRAP_KEY in the environment).
_bootstrap = os.environ.get("BOOTSTRAP_KEY")
if _bootstrap and not db.get_key(_bootstrap):
    db.create_key(_bootstrap, "bootstrap", int(os.environ.get("BOOTSTRAP_MINUTES", "99999")))


# ----------------------------- helpers -----------------------------
def _client_config(cfg: dict) -> dict:
    """Fields the app needs (no admin-only bits)."""
    return {k: cfg[k] for k in ("id", "name", "group", "mode", "sshHost", "sshPort", "username",
                                "password", "proxy", "payload", "sni", "vlessUuid", "wsPath",
                                "wsHost", "dns", "remoteDns", "localSocksPort")}


def require_key(x_api_key: str):
    if not x_api_key:
        raise HTTPException(401, "missing key")
    k = db.get_key(x_api_key.strip())
    if not k:
        raise HTTPException(403, json.dumps({"error": "invalid", "message": "This access key is not valid."}))
    if k["banned"]:
        raise HTTPException(403, json.dumps({"error": "banned", "message": "This access key has been blocked."}))
    return k


def sign(value: str) -> str:
    return hmac.new(COOKIE_SECRET.encode(), value.encode(), hashlib.sha256).hexdigest()


def make_admin_cookie() -> str:
    payload = f"{int(time.time())}"
    return f"{payload}.{sign(payload)}"


def admin_ok(request: Request) -> bool:
    cookie = request.cookies.get("admin")
    if not cookie or "." not in cookie:
        return False
    payload, mac = cookie.rsplit(".", 1)
    if not hmac.compare_digest(mac, sign(payload)):
        return False
    try:
        return (time.time() - int(payload)) < 7 * 86400
    except ValueError:
        return False


def require_admin(request: Request):
    if not admin_ok(request):
        raise HTTPException(401, "login required")


# ----------------------------- client API -----------------------------
@app.get("/api/v1/ping")
def ping():
    s = db.get_settings()
    return {"ok": True, "serverTime": int(time.time()), "minClientVersion": int(s.get("minClientVersion", 0)),
            "announcement": s.get("announcement", "")}


@app.post("/api/v1/auth")
async def auth(request: Request, x_api_key: str = Header(None)):
    k = require_key(x_api_key)
    body = await _json(request)
    device = (body.get("device_id") or "")[:128]
    s = db.get_settings()
    if s.get("bindDeviceToKey") == "1" and k["device"] and device and k["device"] != device:
        raise HTTPException(403, json.dumps({"error": "device",
                            "message": "This key is already in use on another device."}))
    db.touch_key(k["key"], device)
    return {"ok": True, "label": k["label"], "timeRemaining": k["remaining"],
            "banned": False, "freeSessionSeconds": db.setting_int("freeSessionMinutes", 60) * 60,
            "adRewardSeconds": db.setting_int("adRewardMinutes", 60) * 60,
            "startAppId": s.get("startAppId", ""), "announcement": s.get("announcement", "")}


@app.get("/api/v1/configs")
def configs(x_api_key: str = Header(None)):
    k = require_key(x_api_key)
    payload = json.dumps([_client_config(c) for c in db.list_configs(enabled_only=True)]).encode()
    return {"enc": encrypt(APP_SECRET, k["key"], payload)}


@app.post("/api/v1/session/heartbeat")
async def heartbeat(request: Request, x_api_key: str = Header(None)):
    k = require_key(x_api_key)
    body = await _json(request)
    connected = bool(body.get("connected", True))
    remaining = db.consume_time(k["key"], connected)
    return {"timeRemaining": remaining, "expired": remaining <= 0, "banned": False}


@app.post("/api/v1/ad/reward")
async def ad_reward(request: Request, x_api_key: str = Header(None)):
    k = require_key(x_api_key)
    body = await _json(request)
    nonce = (body.get("nonce") or "")[:64]
    if not nonce:
        raise HTTPException(400, "nonce required")
    n, last = db.ad_rate(k["key"], 3600)
    if n >= db.setting_int("adMaxPerHour", 20):
        raise HTTPException(429, json.dumps({"error": "rate", "message": "Too many ads this hour. Try later."}))
    if time.time() - last < db.setting_int("adMinIntervalSeconds", 30):
        raise HTTPException(429, json.dumps({"error": "rate", "message": "Please wait before the next ad."}))
    reward = db.setting_int("adRewardMinutes", 60) * 60
    cap = db.setting_int("maxBankedMinutes", 1440) * 60
    ok, remaining = db.grant_ad(k["key"], nonce, reward, cap)
    return {"ok": ok, "timeRemaining": remaining, "added": reward if ok else 0}


async def _json(request: Request) -> dict:
    try:
        return await request.json()
    except Exception:
        return {}


# ----------------------------- admin -----------------------------
@app.get("/admin", response_class=HTMLResponse)
def admin_page():
    with open(os.path.join(APP_DIR, "static", "admin.html"), encoding="utf-8") as f:
        return f.read()


@app.post("/admin/login")
async def admin_login(request: Request):
    body = await _json(request)
    if not hmac.compare_digest((body.get("password") or ""), ADMIN_PASSWORD):
        raise HTTPException(401, "wrong password")
    resp = JSONResponse({"ok": True})
    resp.set_cookie("admin", make_admin_cookie(), httponly=True, samesite="strict", max_age=7 * 86400)
    return resp


@app.post("/admin/logout")
def admin_logout():
    resp = JSONResponse({"ok": True})
    resp.delete_cookie("admin")
    return resp


@app.get("/admin/api/state")
def admin_state(request: Request):
    require_admin(request)
    return {"configs": db.list_configs(), "keys": db.list_keys(),
            "settings": db.get_settings(), "stats": db.stats()}


@app.post("/admin/api/configs")
async def admin_save_config(request: Request):
    require_admin(request)
    body = await _json(request)
    return {"id": db.save_config(body)}


@app.post("/admin/api/configs/import")
async def admin_import(request: Request):
    require_admin(request)
    body = await _json(request)
    try:
        cfgs = parse_import(body.get("text", ""))
    except Exception as e:
        raise HTTPException(400, f"Could not parse: {e}")
    ids = db.save_configs(cfgs)
    return {"imported": len(ids)}


@app.delete("/admin/api/configs/{cid}")
def admin_delete_config(request: Request, cid: str):
    require_admin(request)
    db.delete_config(cid)
    return {"ok": True}


@app.post("/admin/api/keys")
async def admin_create_keys(request: Request):
    require_admin(request)
    body = await _json(request)
    count = max(1, min(500, int(body.get("count", 1))))
    minutes = int(body.get("minutes", db.setting_int("freeSessionMinutes", 60)))
    label = body.get("label", "")
    created = []
    for _ in range(count):
        key = body.get("key") if count == 1 and body.get("key") else _gen_key()
        db.create_key(key, label, minutes)
        created.append(key)
    return {"keys": created}


@app.post("/admin/api/keys/{key}")
async def admin_update_key(request: Request, key: str):
    require_admin(request)
    body = await _json(request)
    ok = db.update_key(key, banned=body.get("banned"), label=body.get("label"),
                       add_minutes=body.get("addMinutes"), set_minutes=body.get("setMinutes"),
                       reset_device=bool(body.get("resetDevice")))
    if not ok:
        raise HTTPException(404, "no such key")
    return {"ok": True}


@app.delete("/admin/api/keys/{key}")
def admin_delete_key(request: Request, key: str):
    require_admin(request)
    db.delete_key(key)
    return {"ok": True}


@app.post("/admin/api/settings")
async def admin_settings(request: Request):
    require_admin(request)
    body = await _json(request)
    db.set_settings(body)
    return {"ok": True, "settings": db.get_settings()}


def _gen_key() -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "-".join("".join(secrets.choice(alphabet) for _ in range(4)) for _ in range(3))


@app.get("/", response_class=PlainTextResponse)
def root():
    return "Payload Tunnel server. Admin: /admin"
