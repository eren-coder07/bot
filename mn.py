import hashlib
import base64
import json
import time
import os
import requests
import uuid
import random
import string
import threading
import asyncio
import sqlite3
from datetime import datetime, timedelta
from telegram import Update, LabeledPrice, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import (
    Application, CommandHandler, MessageHandler, ContextTypes, filters,
    PreCheckoutQueryHandler, CallbackQueryHandler
)
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

# ---------- Encryption ----------
KEY = "jaidfa2%8iji9iyt"
IV  = "trn6%jiadf8%m9n3"
key_bytes = base64.b64encode(hashlib.sha256(KEY.encode()).digest()).decode()[:32].encode()
iv_bytes  = base64.b64encode(hashlib.sha256(IV.encode()).digest()).decode()[:16].encode()

def encrypt(data):
    cipher = AES.new(key_bytes, AES.MODE_CBC, iv_bytes)
    return base64.b64encode(cipher.encrypt(pad(json.dumps(data, separators=(',', ':')).encode(), AES.block_size))).decode()

def decrypt(data_str):
    raw = unpad(AES.new(key_bytes, AES.MODE_CBC, iv_bytes).decrypt(base64.b64decode(data_str)), AES.block_size)
    return json.loads(raw.decode('utf-8'))

# ---------- Settings ----------
API_BASE             = 'https://api.fowoii.com'
FOLLOW_OFFER_ID      = 4003

TOR_PROXY_HOST = "127.0.0.1"
TOR_PROXY_PORT = 9050
TOR_PROXY = {
    'http': f'socks5h://{TOR_PROXY_HOST}:{TOR_PROXY_PORT}',
    'https': f'socks5h://{TOR_PROXY_HOST}:{TOR_PROXY_PORT}'
}
USE_TOR = True   # MANDATORY – DO NOT DISABLE

BOT_TOKEN = "8636612062:AAG6xdB38mxaxuJsUyCxrTiZ8WLpff1PzXE"   # REPLACE

# ---------- Plans ----------
PLANS = {
    "free":     {"name": "🆓 Free",     "followers_per_day": 10,  "stars": 0},
    "starter":  {"name": "🥉 Starter",  "followers_per_day": 100, "stars": 10},
    "growth":   {"name": "🥈 Growth",   "followers_per_day": 500, "stars": 50},
    "pro":      {"name": "🥇 Pro",      "followers_per_day": 1000,"stars": 100},
    "max":      {"name": "💎 Max",      "followers_per_day": 3000,"stars": 300},
}

# ---------- Database with GLOBAL UNIQUENESS ----------
db_path = "user_registrations.db"

def init_db():
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS users (
                    telegram_id INTEGER PRIMARY KEY,
                    instagram_username TEXT NOT NULL UNIQUE,
                    instagram_user_id TEXT UNIQUE,
                    plan TEXT DEFAULT 'free',
                    last_delivery_date TEXT,
                    delivery_count_today INTEGER DEFAULT 0,
                    registered_at TEXT
                )''')
    conn.commit()
    conn.close()
init_db()

def get_user(telegram_id):
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    c.execute("SELECT * FROM users WHERE telegram_id = ?", (telegram_id,))
    row = c.fetchone()
    conn.close()
    if row:
        return {
            "telegram_id": row[0],
            "instagram_username": row[1],
            "instagram_user_id": row[2],
            "plan": row[3],
            "last_delivery_date": row[4],
            "delivery_count_today": row[5],
            "registered_at": row[6]
        }
    return None

def is_instagram_taken(username, user_id=None):
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    c.execute("SELECT 1 FROM users WHERE instagram_username = ? OR instagram_user_id = ?", (username, user_id))
    taken = c.fetchone() is not None
    conn.close()
    return taken

def register_user(telegram_id, instagram_username, instagram_user_id):
    if is_instagram_taken(instagram_username, instagram_user_id):
        return False, "This Instagram account is already registered by another user."
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    now = datetime.now().isoformat()
    try:
        c.execute("INSERT INTO users (telegram_id, instagram_username, instagram_user_id, plan, last_delivery_date, delivery_count_today, registered_at) VALUES (?, ?, ?, 'free', NULL, 0, ?)",
                  (telegram_id, instagram_username, instagram_user_id, now))
        conn.commit()
        return True, "Success"
    except sqlite3.IntegrityError as e:
        return False, f"Database error: {e}"
    finally:
        conn.close()

def update_user_plan(telegram_id, new_plan):
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    c.execute("UPDATE users SET plan = ? WHERE telegram_id = ?", (new_plan, telegram_id))
    conn.commit()
    conn.close()

def update_last_delivery(telegram_id, delivery_count):
    today = datetime.now().date().isoformat()
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    c.execute("UPDATE users SET last_delivery_date = ?, delivery_count_today = ? WHERE telegram_id = ?",
              (today, delivery_count, telegram_id))
    conn.commit()
    conn.close()

def get_all_users():
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    c.execute("SELECT telegram_id, instagram_username, instagram_user_id, plan, last_delivery_date FROM users")
    rows = c.fetchall()
    conn.close()
    return rows

# ---------- Instagram ID Resolver ----------
def get_instagram_id(username):
    username = username.strip().lstrip('@')
    url = f"https://www.instagram.com/{username}/?__a=1&__d=1"
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
    proxies = TOR_PROXY if USE_TOR else None
    try:
        r = requests.get(url, headers=headers, timeout=10, proxies=proxies)
        if r.status_code == 200:
            data = r.json()
            return str(data["graphql"]["user"]["id"])
    except:
        pass
    alt_url = f"https://api.allorigins.win/raw?url=https://www.instagram.com/{username}/?__a=1"
    try:
        r = requests.get(alt_url, timeout=10, proxies=proxies)
        if r.status_code == 200:
            data = r.json()
            return str(data["graphql"]["user"]["id"])
    except:
        pass
    return None

# ---------- Follower Delivery (Tor enforced) ----------
BASE_HEADERS = {
    'Content-Type': 'application/json;charset=UTF-8',
    'Accept': 'application/json, text/plain, */*',
    'User-Agent': 'Mozilla/5.0 (Linux; Android 12; SM-A025F) AppleWebKit/537.36',
    'X-Requested-With': 'com.space.mass.likes',
    'App-Version': '1.4.0',
}

def get_session_headers():
    h = BASE_HEADERS.copy()
    ip = f"{random.randint(1,255)}.{random.randint(0,255)}.{random.randint(0,255)}.{random.randint(1,254)}"
    h['X-Forwarded-For'] = ip
    return h

def generate_random_credentials():
    dev_id = str(uuid.uuid4())
    return {
        "userId": None, "instUserId": None, "deviceId": dev_id, "loginId": dev_id,
        "android_id": str(uuid.uuid4()), "gaid": str(uuid.uuid4()), "unique_id": str(uuid.uuid4()),
        "deviceModel": random.choice(["Samsung Galaxy S21", "SM-A025F", "Pixel 6"]),
        "systemVersion": random.choice(["12", "13"]), "registered": False
    }

def get_base_body(creds):
    return {
        "w": True, "productId": "com.space.mass.likes", "instUserId": creds.get("instUserId"),
        "version": "1.4.0", "deviceInfo": {
            "deviceId": creds["deviceId"], "timezone": "+0100", "timestamp": str(int(time.time() * 1000)),
            "lang": "en", "deviceModel": creds.get("deviceModel"), "systemVersion": creds.get("systemVersion"),
            "regionCode": random.choice(["US", "GB"]), "android_id": creds["android_id"],
            "gaid": creds["gaid"], "unique_id": creds["unique_id"]
        }, "userId": creds.get("userId")
    }

def send_request(creds, path, extra=None):
    body = get_base_body(creds)
    if extra: body.update(extra)
    proxies = TOR_PROXY if USE_TOR else None
    for _ in range(3):
        try:
            r = requests.post(f"{API_BASE}{path}", json={"data": encrypt(body)}, headers=get_session_headers(), timeout=30, proxies=proxies)
            if not r.text: continue
            res = r.json()
            if "data" in res: return decrypt(res["data"])
            return res
        except Exception as e:
            print(f"Request error: {e}")
            time.sleep(10 if USE_TOR else 5)
    return {}

def extract_balance(res):
    ui = res.get("userInfo", {})
    return ui.get("balance") or ui.get("coins") or res.get("balance")

def login(creds):
    res = send_request(creds, '/api/v1/user/login', {"loginId": creds["deviceId"], "otherLoginIds": [creds["deviceId"]]})
    if res.get("code") == 0:
        ui = res.get("userInfo", {})
        if ui.get("userId"): creds["userId"] = ui["userId"]
        creds["registered"] = True
    return res

def do_ad_task(creds, product_id, wait=2):
    ts = int(time.time() * 1000)
    start = send_request(creds, '/api/v1/ad/start', {"targetProductId": product_id, "otherLoginIds": [creds["deviceId"]]})
    if not start or start.get("code") != 0: return None
    time.sleep(wait)
    send_request(creds, '/api/v1/hawkeye/get')
    comp = send_request(creds, '/api/v1/ad/complete', {"targetProductId": product_id, "financeTimestamp": ts})
    if comp and comp.get("code") == 0: return extract_balance(comp)
    return None

def place_follow_order(creds, target_inst_id, target_username):
    body = get_base_body(creds)
    body["instUserId"] = target_inst_id
    body["deviceInfo"]["timestamp"] = str(int(time.time() * 1000))
    body.update({
        "offerId": FOLLOW_OFFER_ID,
        "insUserInfo": {"instaUsername": target_username, "instaFullName": target_username,
                        "profileUrl": f"https://instagram.com/{target_username}", "instUserId": target_inst_id},
        "reqId": str(uuid.uuid4()),
    })
    proxies = TOR_PROXY if USE_TOR else None
    try:
        r = requests.post(f"{API_BASE}/api/v1/order/create", json={"data": encrypt(body)}, headers=get_session_headers(), timeout=30, proxies=proxies)
        if not r.text: return False, None
        res = r.json()
        if "data" in res: res = decrypt(res["data"])
        if res.get("code") == 0: return True, extract_balance(res)
        return False, res.get("message")
    except Exception as e: return False, str(e)

def deliver_followers(target_username, target_inst_id, requested_count):
    delivered = 0
    for _ in range(requested_count * 2):
        if delivered >= requested_count: break
        creds = generate_random_credentials()
        login_res = login(creds)
        if login_res.get("code") != 0: time.sleep(3); continue
        coins = extract_balance(login_res) or 0
        for pid in ["com.f4f.tagmaster", "com.fame.plus.follow", "com.soogrow.nitreo"]:
            if coins >= 150: break
            new_bal = do_ad_task(creds, pid)
            if new_bal: coins = new_bal
            time.sleep(1)
        if coins < 150:
            new_bal = do_ad_task(creds, "com.task.rate.us")
            if new_bal: coins = new_bal
        if coins >= 150:
            success, _ = place_follow_order(creds, target_inst_id, target_username)
            if success: delivered += 1
        time.sleep(5)
    return delivered

# ---------- Daily Delivery Scheduler (Tor enabled) ----------
scheduler_running = True
main_loop = None

async def send_telegram_message(chat_id, text):
    if main_loop and not main_loop.is_closed():
        app = Application.current()
        await app.bot.send_message(chat_id=chat_id, text=text)

def delivery_worker():
    global scheduler_running
    while scheduler_running:
        today_str = datetime.now().date().isoformat()
        for (telegram_id, insta_username, insta_user_id, plan_key, last_delivery) in get_all_users():
            if last_delivery == today_str:
                continue
            plan = PLANS.get(plan_key, PLANS["free"])
            target_count = plan["followers_per_day"]
            if target_count <= 0: continue
            if not insta_user_id:
                insta_user_id = get_instagram_id(insta_username)
                if not insta_user_id:
                    asyncio.run_coroutine_threadsafe(
                        send_telegram_message(telegram_id, f"⚠️ Could not resolve ID for @{insta_username}. Delivery skipped."),
                        main_loop
                    )
                    update_last_delivery(telegram_id, 0)
                    continue
                conn = sqlite3.connect(db_path)
                c = conn.cursor()
                c.execute("UPDATE users SET instagram_user_id = ? WHERE telegram_id = ?", (insta_user_id, telegram_id))
                conn.commit()
                conn.close()
            asyncio.run_coroutine_threadsafe(
                send_telegram_message(telegram_id, f"🌟 Starting delivery: {target_count} followers to @{insta_username} ..."),
                main_loop
            )
            delivered = deliver_followers(insta_username, insta_user_id, target_count)
            update_last_delivery(telegram_id, delivered)
            asyncio.run_coroutine_threadsafe(
                send_telegram_message(telegram_id, f"✅ Delivered {delivered}/{target_count} followers today."),
                main_loop
            )
        time.sleep(3600)

# ---------- Telegram Handlers ----------
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    user_data = get_user(user_id)
    if user_data:
        plan = PLANS[user_data["plan"]]
        await update.message.reply_text(
            f"👋 Welcome back!\n\n"
            f"📌 Registered: @{user_data['instagram_username']}\n"
            f"📦 Plan: {plan['name']} – {plan['followers_per_day']}/day\n"
            f"✅ Auto-delivery active.\n\n"
            f"/upgrade – change plan\n/status – info"
        )
    else:
        await update.message.reply_text(
            "🤖 *Instagram Follower Bot*\n\n"
            "Send me your **Instagram username** to register.\n"
            "Example: `@john_doe` or `john_doe`\n\n"
            "⚠️ Each Instagram account can be registered by **only one** Telegram user."
        )

async def handle_username(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    username = update.message.text.strip().lstrip('@')
    if get_user(user_id):
        await update.message.reply_text("❌ You are already registered. Use /upgrade to change plan.")
        return
    if len(username) < 3 or not username.replace('_', '').replace('.', '').isalnum():
        await update.message.reply_text("❌ Invalid username.")
        return
    await update.message.reply_text("🔍 Verifying Instagram account...")
    insta_id = get_instagram_id(username)
    if not insta_id:
        await update.message.reply_text("❌ Could not find that Instagram username. Make sure it exists and is public.")
        return
    if is_instagram_taken(username, insta_id):
        await update.message.reply_text("❌ This Instagram account is already registered by another user. No duplicates allowed.")
        return
    success, msg = register_user(user_id, username, insta_id)
    if success:
        await update.message.reply_text(
            f"✅ Registered @{username}!\n\n"
            f"📦 **Free plan** – 10 followers/day.\n"
            f"Use /upgrade to get more.\n\n"
            f"Delivery starts within 24 hours."
        )
    else:
        await update.message.reply_text(f"❌ Registration failed: {msg}")

async def upgrade(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    if not get_user(user_id):
        await update.message.reply_text("❌ Register first by sending your Instagram username.")
        return
    keyboard = [[InlineKeyboardButton(f"{PLANS[k]['name']} – {PLANS[k]['followers_per_day']}/day – {PLANS[k]['stars']}⭐", callback_data=f"plan_{k}")] for k in PLANS if k != "free"]
    keyboard.append([InlineKeyboardButton("💰 Contact admin", callback_data="contact_admin")])
    await update.message.reply_text("Select a plan:", reply_markup=InlineKeyboardMarkup(keyboard))

async def plan_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    data = query.data
    if data == "contact_admin":
        await query.edit_message_text("📧 Contact @admin_username (replace with actual admin handle).")
        return
    if data.startswith("plan_"):
        plan_key = data.split("_")[1]
        plan = PLANS[plan_key]
        await context.bot.send_invoice(
            chat_id=query.from_user.id,
            title=f"Upgrade to {plan['name']}",
            description=f"{plan['followers_per_day']} followers/day",
            payload=f"upgrade_{plan_key}",
            provider_token="",
            currency="XTR",
            prices=[LabeledPrice(label=plan["name"], amount=plan["stars"] * 100)],
            start_parameter="upgrade_subscription"
        )

async def pre_checkout(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.pre_checkout_query.answer(ok=True)

async def successful_payment(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    payload = update.message.successful_payment.invoice_payload
    if payload.startswith("upgrade_"):
        plan_key = payload.split("_")[1]
        update_user_plan(user_id, plan_key)
        await update.message.reply_text(f"✅ Upgraded to {PLANS[plan_key]['name']} – {PLANS[plan_key]['followers_per_day']} followers/day!")

async def status_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_data = get_user(update.effective_user.id)
    if not user_data:
        await update.message.reply_text("❌ Not registered. Send your Instagram username.")
        return
    plan = PLANS[user_data["plan"]]
    last = user_data["last_delivery_date"] or "Never"
    await update.message.reply_text(
        f"📊 *Status*\n"
        f"Instagram: @{user_data['instagram_username']}\n"
        f"Plan: {plan['name']} ({plan['followers_per_day']}/day)\n"
        f"Last delivery: {last} ({user_data['delivery_count_today']} delivered)\n"
        f"Next: {'Today' if last != datetime.now().date().isoformat() else 'Tomorrow'}"
    )

async def stop_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("To stop delivery, contact admin. Registrations cannot be deleted automatically.")

# ---------- Main ----------
def main():
    global main_loop, scheduler_running
    if not BOT_TOKEN or len(BOT_TOKEN) < 20:
        print("ERROR: Set your BOT_TOKEN")
        return
    if USE_TOR:
        print("[!] Tor proxy is MANDATORY – ensure Tor is running on 127.0.0.1:9050")
    threading.Thread(target=delivery_worker, daemon=True).start()
    app = Application.builder().token(BOT_TOKEN).build()
    async def set_loop(): global main_loop; main_loop = asyncio.get_running_loop()
    app.post_init = set_loop
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("upgrade", upgrade))
    app.add_handler(CommandHandler("status", status_cmd))
    app.add_handler(CommandHandler("stop", stop_cmd))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_username))
    app.add_handler(CallbackQueryHandler(plan_callback))
    app.add_handler(PreCheckoutQueryHandler(pre_checkout))
    app.add_handler(MessageHandler(filters.SUCCESSFUL_PAYMENT, successful_payment))
    print("[*] Bot running with Tor proxy (mandatory)...")
    app.run_polling(allowed_updates=Update.ALL_TYPES)

if __name__ == "__main__":
    main()