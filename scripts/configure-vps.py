#!/usr/bin/env python3
"""Create a private VPS dotenv file interactively, without printing secrets."""
import argparse
import base64
import getpass
import os
from pathlib import Path
import re
import secrets


def configuration(domain, image, owner_email, sender, smtp_key):
    labels = domain.split(".")
    if (len(domain) > 253 or len(labels) < 2
            or any(not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label)
                   for label in labels)
            or labels[-1].isdigit()):
        raise ValueError("Use a lowercase public DNS name, without https://, a port, or path")
    if not re.fullmatch(r"ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}", image):
        raise ValueError("Use the complete ghcr.io/...@sha256:... image from the CI summary")
    for email in (owner_email, sender):
        if len(email) > 254 or not re.fullmatch(r"[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", email):
            raise ValueError("Enter valid owner and verified sender email addresses")
    if not re.fullmatch(r"re_[A-Za-z0-9_-]+", smtp_key):
        raise ValueError("Enter a Resend sending API key (re_...), not a placeholder")
    return {
        "APP_DOMAIN": domain,
        "APP_IMAGE": image,
        "POSTGRES_ADMIN_PASSWORD": secrets.token_hex(32),
        "DATABASE_PASSWORD": secrets.token_hex(32),
        "APP_AUTH_JWT_SECRET": secrets.token_hex(32),
        "APP_PLATFORM_USERNAME": "owner",
        "APP_PLATFORM_EMAIL": owner_email,
        "APP_PLATFORM_PASSWORD": secrets.token_urlsafe(30),
        "APP_PLATFORM_TOTP_SECRET": base64.b32encode(secrets.token_bytes(20)).decode(),
        "SMTP_HOST": "smtp.resend.com",
        "SMTP_PORT": "2587",
        "SMTP_USERNAME": "resend",
        "SMTP_PASSWORD": smtp_key,
        "APP_MAIL_FROM": sender,
    }


def write_configuration(path, values):
    # Fail closed on an existing file/symlink: reruns must not rotate live DB or MFA secrets.
    content = "# Private production configuration. Never commit, paste, or screenshot.\n"
    for key, value in values.items():
        if any(char in value for char in ("'", "\n", "\r", "\0")):
            raise ValueError("Configuration values must be single-line and contain no single quotes")
        content += f"{key}='{value}'\n"
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("/opt/servemytable/.env"))
    args = parser.parse_args()
    if args.output.exists() or args.output.is_symlink():
        parser.error("Configuration already exists; edit it in place, never regenerate live secrets")
    try:
        domain = input("App hostname [orders.servemytable.com]: ").strip() or "orders.servemytable.com"
        image = input("Digest-pinned APP_IMAGE from GitHub Actions: ").strip()
        owner = input("Your real owner email address: ").strip()
        sender = input("Verified sending email address (e.g. orders@mail.servemytable.com): ").strip()
        key = getpass.getpass("Resend sending API key (hidden): ").strip()
        write_configuration(args.output, configuration(domain, image, owner, sender, key))
    except (ValueError, OSError) as error:
        parser.error(str(error))
    print(f"Created {args.output} with owner-only permissions. No secrets were printed.")
    print("View it privately to store the owner password and TOTP secret in your password manager.")
    print("Do not run docker compose config without --quiet: it would print the secrets.")


if __name__ == "__main__":
    main()
