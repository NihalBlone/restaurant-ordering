#!/usr/bin/env python3
"""Print fresh bootstrap secrets locally. Never run this in CI or commit its output."""
import base64
import secrets

if __name__ == "__main__":
    print("Store these in your password manager and Render environment, not in Git.")
    print("APP_AUTH_JWT_SECRET=" + secrets.token_hex(32))
    print("APP_PLATFORM_PASSWORD=" + secrets.token_urlsafe(30))
    print("APP_PLATFORM_TOTP_SECRET=" + base64.b32encode(secrets.token_bytes(20)).decode())
