#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Pushover notification helpers.

Backward compatible:
- send_pushover_notification(title, message, sound="...")

New:
- send_pushover_notifications(title, message, user_keys=[...], sound="...")

Environment variables:
- PUSHOVER_API_TOKEN: app token
- PUSHOVER_USER_KEY: single default user key
- PUSHOVER_USER_KEYS: comma-separated user keys (batch default, preferred)
"""

import logging
import os

import requests
from dotenv import load_dotenv

load_dotenv()

PUSHOVER_API_URL = "https://api.pushover.net/1/messages.json"
PUSHOVER_API_TOKEN = os.getenv("PUSHOVER_API_TOKEN")
PUSHOVER_USER_KEY = os.getenv("PUSHOVER_USER_KEY")
PUSHOVER_USER_KEYS = os.getenv("PUSHOVER_USER_KEYS", "")


def _parse_user_keys(value: str) -> list[str]:
    keys: list[str] = []
    for raw in str(value or "").split(","):
        key = raw.strip()
        if not key:
            continue
        if key in keys:
            continue
        keys.append(key)
    return keys


def _resolve_user_keys(user_keys: list[str] | None = None) -> list[str]:
    if user_keys is not None:
        return _parse_user_keys(",".join([str(x) for x in user_keys]))

    keys_from_env = _parse_user_keys(PUSHOVER_USER_KEYS)
    if keys_from_env:
        return keys_from_env

    return _parse_user_keys(PUSHOVER_USER_KEY)


def send_pushover_notification(
        title,
        message,
        sound="cashregister",
        user_key: str | None = None,
) -> bool:
    """
    Send to a single user.
    If user_key is None, fallback order is:
    1) first from PUSHOVER_USER_KEYS
    2) PUSHOVER_USER_KEY
    """
    target_user_key = str(user_key).strip() if user_key else ""
    if not target_user_key:
        resolved = _resolve_user_keys()
        target_user_key = resolved[0] if resolved else ""

    if not PUSHOVER_API_TOKEN:
        logging.error("PUSHOVER_API_TOKEN is empty.")
        return False
    if not target_user_key:
        logging.error("No Pushover user key found. Set PUSHOVER_USER_KEY or PUSHOVER_USER_KEYS.")
        return False

    data = {
        "token": PUSHOVER_API_TOKEN,
        "user": target_user_key,
        "title": title,
        "message": message,
        "sound": sound,
        "priority": 2,
        "retry": 30,
        "expire": 10800,
    }

    try:
        response = requests.post(PUSHOVER_API_URL, data=data, timeout=15)
    except requests.RequestException as e:
        logging.error("Failed to send notification: user=%s, detail=%s", target_user_key, e)
        return False

    if response.status_code == 200:
        logging.info("Notification sent successfully! user=%s", target_user_key)
        return True

    logging.error("Failed to send notification: user=%s, detail=%s", target_user_key, response.text)
    return False


def send_pushover_notifications(
        title,
        message,
        user_keys: list[str] | None = None,
        sound="cashregister",
) -> list[dict]:
    """
    Send the same message to multiple users.
    If user_keys is None, fallback order is:
    1) PUSHOVER_USER_KEYS (comma-separated)
    2) PUSHOVER_USER_KEY
    """
    targets = _resolve_user_keys(user_keys)
    if not targets:
        logging.error("No Pushover user keys found for batch send.")
        return []

    results: list[dict] = []
    for target in targets:
        ok = send_pushover_notification(
            title=title,
            message=message,
            sound=sound,
            user_key=target,
        )
        results.append({
            "user_key": target,
            "ok": bool(ok),
        })
    return results


if __name__ == "__main__":
    send_pushover_notifications("Title", "test message")
