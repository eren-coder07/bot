"""AES-256-GCM used to encrypt config payloads to the app.

The key is derived from a shared APP_SECRET (compiled into the app and set on the
server) and the user's access key, so a captured response is useless without both.
"""
import base64
import hashlib
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def _key(app_secret: str, access_key: str) -> bytes:
    return hashlib.sha256(f"{app_secret}:{access_key}".encode()).digest()


def encrypt(app_secret: str, access_key: str, plaintext: bytes) -> str:
    nonce = os.urandom(12)
    ct = AESGCM(_key(app_secret, access_key)).encrypt(nonce, plaintext, None)
    return base64.b64encode(nonce + ct).decode()


def decrypt(app_secret: str, access_key: str, blob: str) -> bytes:
    raw = base64.b64decode(blob)
    nonce, ct = raw[:12], raw[12:]
    return AESGCM(_key(app_secret, access_key)).decrypt(nonce, ct, None)
