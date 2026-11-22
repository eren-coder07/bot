"""SQLite storage for configs, access keys and settings."""
import json
import os
import sqlite3
import threading
import time

_LOCK = threading.Lock()
DB_PATH = os.environ.get("DB_PATH", os.path.join(os.path.dirname(__file__), "data.db"))

DEFAULT_SETTINGS = {
    "freeSessionMinutes": "60",
    "adRewardMinutes": "60",
    "maxBankedMinutes": "1440",          # cap on how much time a key can hold
    "adMinIntervalSeconds": "30",        # anti-spam between rewarded ads
    "adMaxPerHour": "20",
    "startAppId": "210964446",
    "bindDeviceToKey": "0",              # 1 = a key only works on the first device that used it
    "minClientVersion": "0",             # force-update gate (versionCode)
    "announcement": "",                  # shown in the app
}


def _conn():
    c = sqlite3.connect(DB_PATH, timeout=30)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    return c


def init(seed_configs=None):
    with _LOCK, _conn() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS configs (
                id TEXT PRIMARY KEY, name TEXT, grp TEXT, mode TEXT,
                sshHost TEXT, sshPort INTEGER, username TEXT, password TEXT,
                proxy TEXT, payload TEXT, sni TEXT, vlessUuid TEXT,
                wsPath TEXT, wsHost TEXT, dns TEXT, remoteDns INTEGER,
                localSocksPort INTEGER, sort INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1,
                created REAL
            );
            CREATE TABLE IF NOT EXISTS keys (
                key TEXT PRIMARY KEY, label TEXT, banned INTEGER DEFAULT 0,
                remaining INTEGER DEFAULT 0, device TEXT, created REAL,
                lastSeen REAL, lastHeartbeat REAL, addedTotal INTEGER DEFAULT 0,
                adCount INTEGER DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS ad_nonces (key TEXT, nonce TEXT, ts REAL, PRIMARY KEY(key, nonce));
            CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT);
            """
        )
        for k, v in DEFAULT_SETTINGS.items():
            c.execute("INSERT OR IGNORE INTO settings(k, v) VALUES(?, ?)", (k, v))
        n = c.execute("SELECT COUNT(*) AS n FROM configs").fetchone()["n"]
        if n == 0 and seed_configs:
            for i, cfg in enumerate(seed_configs):
                _upsert_config(c, {**cfg, "sort": i})


# ---------------- settings ----------------
def get_settings():
    with _LOCK, _conn() as c:
        return {r["k"]: r["v"] for r in c.execute("SELECT k, v FROM settings")}


def set_settings(d: dict):
    with _LOCK, _conn() as c:
        for k, v in d.items():
            c.execute("INSERT INTO settings(k, v) VALUES(?, ?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", (k, str(v)))


def setting_int(name, default=0):
    try:
        return int(get_settings().get(name, default))
    except (ValueError, TypeError):
        return default


# ---------------- configs ----------------
_CONFIG_FIELDS = ("id", "name", "group", "mode", "sshHost", "sshPort", "username", "password",
                  "proxy", "payload", "sni", "vlessUuid", "wsPath", "wsHost", "dns",
                  "remoteDns", "localSocksPort", "sort", "enabled")


def _row_to_config(r):
    return {
        "id": r["id"], "name": r["name"], "group": r["grp"] or "", "mode": r["mode"],
        "sshHost": r["sshHost"], "sshPort": r["sshPort"], "username": r["username"] or "",
        "password": r["password"] or "", "proxy": r["proxy"] or "", "payload": r["payload"] or "",
        "sni": r["sni"] or "", "vlessUuid": r["vlessUuid"] or "", "wsPath": r["wsPath"] or "/",
        "wsHost": r["wsHost"] or "", "dns": r["dns"] or "1.1.1.1, 8.8.8.8",
        "remoteDns": bool(r["remoteDns"]), "localSocksPort": r["localSocksPort"] or 1080,
        "sort": r["sort"], "enabled": bool(r["enabled"]),
    }


def _upsert_config(c, cfg):
    import uuid
    cid = cfg.get("id") or uuid.uuid4().hex
    c.execute(
        """INSERT INTO configs(id,name,grp,mode,sshHost,sshPort,username,password,proxy,payload,
             sni,vlessUuid,wsPath,wsHost,dns,remoteDns,localSocksPort,sort,enabled,created)
           VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
           ON CONFLICT(id) DO UPDATE SET name=excluded.name,grp=excluded.grp,mode=excluded.mode,
             sshHost=excluded.sshHost,sshPort=excluded.sshPort,username=excluded.username,
             password=excluded.password,proxy=excluded.proxy,payload=excluded.payload,sni=excluded.sni,
             vlessUuid=excluded.vlessUuid,wsPath=excluded.wsPath,wsHost=excluded.wsHost,dns=excluded.dns,
             remoteDns=excluded.remoteDns,localSocksPort=excluded.localSocksPort,sort=excluded.sort,
             enabled=excluded.enabled""",
        (cid, cfg.get("name", ""), cfg.get("group", ""), cfg.get("mode", "SSH_PROXY_PAYLOAD"),
         cfg.get("sshHost", ""), int(cfg.get("sshPort", 22) or 22), cfg.get("username", ""),
         cfg.get("password", ""), cfg.get("proxy", ""), cfg.get("payload", ""), cfg.get("sni", ""),
         cfg.get("vlessUuid", ""), cfg.get("wsPath", "/") or "/", cfg.get("wsHost", ""),
         cfg.get("dns", "1.1.1.1, 8.8.8.8"), 1 if cfg.get("remoteDns", True) else 0,
         int(cfg.get("localSocksPort", 1080) or 1080), int(cfg.get("sort", 0) or 0),
         1 if cfg.get("enabled", True) else 0, time.time()),
    )
    return cid


def list_configs(enabled_only=False):
    with _LOCK, _conn() as c:
        q = "SELECT * FROM configs" + (" WHERE enabled=1" if enabled_only else "") + " ORDER BY sort, created"
        return [_row_to_config(r) for r in c.execute(q)]


def save_config(cfg):
    with _LOCK, _conn() as c:
        return _upsert_config(c, cfg)


def save_configs(cfgs):
    with _LOCK, _conn() as c:
        base = c.execute("SELECT COALESCE(MAX(sort), -1) AS m FROM configs").fetchone()["m"] + 1
        return [_upsert_config(c, {**cfg, "sort": cfg.get("sort", base + i)}) for i, cfg in enumerate(cfgs)]


def delete_config(cid):
    with _LOCK, _conn() as c:
        c.execute("DELETE FROM configs WHERE id=?", (cid,))


# ---------------- keys ----------------
def _row_to_key(r):
    return {
        "key": r["key"], "label": r["label"] or "", "banned": bool(r["banned"]),
        "remaining": r["remaining"] or 0, "device": r["device"] or "", "created": r["created"],
        "lastSeen": r["lastSeen"], "addedTotal": r["addedTotal"] or 0, "adCount": r["adCount"] or 0,
    }


def list_keys():
    with _LOCK, _conn() as c:
        return [_row_to_key(r) for r in c.execute("SELECT * FROM keys ORDER BY created DESC")]


def get_key(key):
    with _LOCK, _conn() as c:
        r = c.execute("SELECT * FROM keys WHERE key=?", (key,)).fetchone()
        return _row_to_key(r) if r else None


def create_key(key, label, minutes):
    with _LOCK, _conn() as c:
        c.execute("INSERT OR IGNORE INTO keys(key,label,remaining,created,addedTotal) VALUES(?,?,?,?,?)",
                  (key, label, minutes * 60, time.time(), minutes * 60))


def update_key(key, banned=None, label=None, add_minutes=None, set_minutes=None, reset_device=False):
    with _LOCK, _conn() as c:
        r = c.execute("SELECT * FROM keys WHERE key=?", (key,)).fetchone()
        if not r:
            return None
        remaining = r["remaining"] or 0
        if set_minutes is not None:
            remaining = max(0, int(set_minutes) * 60)
        if add_minutes:
            remaining = max(0, remaining + int(add_minutes) * 60)
        c.execute(
            "UPDATE keys SET banned=?, label=?, remaining=?, device=? WHERE key=?",
            (int(banned if banned is not None else r["banned"]),
             label if label is not None else r["label"], remaining,
             "" if reset_device else r["device"], key),
        )
        return True


def delete_key(key):
    with _LOCK, _conn() as c:
        c.execute("DELETE FROM keys WHERE key=?", (key,))
        c.execute("DELETE FROM ad_nonces WHERE key=?", (key,))


def touch_key(key, device=None):
    with _LOCK, _conn() as c:
        if device is not None:
            c.execute("UPDATE keys SET lastSeen=?, device=CASE WHEN device IS NULL OR device='' THEN ? ELSE device END WHERE key=?",
                      (time.time(), device, key))
        else:
            c.execute("UPDATE keys SET lastSeen=? WHERE key=?", (time.time(), key))


def consume_time(key, connected):
    """On heartbeat: subtract wall-clock time since last heartbeat while connected. Returns remaining seconds."""
    with _LOCK, _conn() as c:
        r = c.execute("SELECT remaining, lastHeartbeat FROM keys WHERE key=?", (key,)).fetchone()
        if not r:
            return 0
        now = time.time()
        remaining = r["remaining"] or 0
        last = r["lastHeartbeat"]
        if connected and last:
            elapsed = min(max(0, now - last), 120)  # cap so a long gap can't drain everything
            remaining = max(0, int(remaining - elapsed))
        c.execute("UPDATE keys SET remaining=?, lastHeartbeat=?, lastSeen=? WHERE key=?",
                  (remaining, now if connected else None, now, key))
        return remaining


def grant_ad(key, nonce, reward_seconds, cap_seconds):
    """Add ad reward once per nonce. Returns (ok, remaining)."""
    with _LOCK, _conn() as c:
        r = c.execute("SELECT remaining, adCount, addedTotal FROM keys WHERE key=?", (key,)).fetchone()
        if not r:
            return False, 0
        try:
            c.execute("INSERT INTO ad_nonces(key, nonce, ts) VALUES(?,?,?)", (key, nonce, time.time()))
        except sqlite3.IntegrityError:
            return False, r["remaining"] or 0  # replay
        remaining = min(cap_seconds, (r["remaining"] or 0) + reward_seconds)
        c.execute("UPDATE keys SET remaining=?, adCount=adCount+1, addedTotal=addedTotal+? WHERE key=?",
                  (remaining, reward_seconds, key))
        return True, remaining


def ad_rate(key, window_seconds):
    with _LOCK, _conn() as c:
        since = time.time() - window_seconds
        n = c.execute("SELECT COUNT(*) AS n FROM ad_nonces WHERE key=? AND ts>?", (key, since)).fetchone()["n"]
        last = c.execute("SELECT MAX(ts) AS t FROM ad_nonces WHERE key=?", (key,)).fetchone()["t"]
        return n, (last or 0)


def stats():
    with _LOCK, _conn() as c:
        now = time.time()
        return {
            "configs": c.execute("SELECT COUNT(*) AS n FROM configs").fetchone()["n"],
            "keys": c.execute("SELECT COUNT(*) AS n FROM keys").fetchone()["n"],
            "banned": c.execute("SELECT COUNT(*) AS n FROM keys WHERE banned=1").fetchone()["n"],
            "activeNow": c.execute("SELECT COUNT(*) AS n FROM keys WHERE lastHeartbeat>?", (now - 120,)).fetchone()["n"],
            "onlineToday": c.execute("SELECT COUNT(*) AS n FROM keys WHERE lastSeen>?", (now - 86400,)).fetchone()["n"],
        }
