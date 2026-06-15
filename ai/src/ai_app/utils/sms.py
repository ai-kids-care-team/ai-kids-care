#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Simple SMS sender using Solapi SDK.

Supports:
- batch send

Environment variables:
- SOLAPI_API_KEY
- SOLAPI_API_SECRET
- SMS_DEFAULT_SENDER (optional)
- SMS_DEFAULT_RECIPIENTS (optional, comma-separated)
"""
from __future__ import annotations

import os
import re
from pathlib import Path

import pandas as pd
from dotenv import load_dotenv


def normalize_phone(value: str) -> str:
    digits = re.sub(r"\D+", "", str(value))
    if not digits:
        raise ValueError(f"Invalid phone number: {value}")
    return digits


def build_message_service(api_key: str, api_secret: str):
    if not api_key or not api_secret:
        raise ValueError("SOLAPI_API_KEY / SOLAPI_API_SECRET is empty.")
    try:
        from solapi import SolapiMessageService
    except ImportError as e:
        raise ImportError(
            "solapi package is not installed. Install it in your .venv first."
        ) from e
    return SolapiMessageService(api_key=api_key, api_secret=api_secret)


def build_request_message(sender: str, recipient: str, text: str):
    try:
        from solapi.model import RequestMessage
    except ImportError as e:
        raise ImportError(
            "solapi package is not installed. Install it in your .venv first."
        ) from e

    return RequestMessage(
        from_=normalize_phone(sender),
        to=normalize_phone(recipient),
        text=str(text),
    )


def send_sms_single(
        message_service,
        sender: str,
        recipient: str,
        text: str,
) -> dict:
    message = build_request_message(sender=sender, recipient=recipient, text=text)
    try:
        response = message_service.send(message)
        group_id = getattr(getattr(response, "group_info", None), "group_id", "")
        return {
            "to": normalize_phone(recipient),
            "ok": True,
            "group_id": str(group_id),
            "error": "",
        }
    except Exception as e:
        return {
            "to": normalize_phone(recipient),
            "ok": False,
            "group_id": "",
            "error": str(e),
        }


def send_sms_batch(
        message_service,
        sender: str,
        recipients: list[str],
        text: str,
) -> list[dict]:
    normalized: list[str] = []
    for value in recipients:
        phone = normalize_phone(value)
        if phone in normalized:
            continue
        normalized.append(phone)

    results: list[dict] = []
    for phone in normalized:
        one = send_sms_single(
            message_service=message_service,
            sender=sender,
            recipient=phone,
            text=text,
        )
        results.append(one)
    return results


def parse_recipients(value: str) -> list[str]:
    recipients: list[str] = []
    for raw in str(value or "").split(","):
        item = raw.strip()
        if not item:
            continue
        recipients.append(item)
    return recipients


def print_results(results: list[dict]) -> None:
    total = len(results)
    success = sum(1 for item in results if item.get("ok"))
    failed = total - success

    print("\n===== SMS Send Summary =====")
    print(f"total: {total}")
    print(f"success: {success}")
    print(f"failed: {failed}")

    if failed > 0:
        print("\nFailed items:")
        for item in results:
            if item.get("ok"):
                continue
            print(f"to={item.get('to')}, error={item.get('error')}")


def save_results_csv(results: list[dict], output_csv: Path | None) -> None:
    if output_csv is None:
        return
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(results).to_csv(output_csv, index=False, encoding="utf-8")
    print(f"results_csv: {output_csv}")


if __name__ == "__main__":
    load_dotenv()

    api_key = os.getenv("SOLAPI_API_KEY")
    api_secret = os.getenv("SOLAPI_API_SECRET")
    sender = os.getenv("SMS_DEFAULT_SENDER")
    recipients = parse_recipients(os.getenv("SMS_DEFAULT_RECIPIENTS"))
    if not recipients:
        raise ValueError("SMS_DEFAULT_RECIPIENTS is empty. Use comma-separated phone numbers.")

    # Common text
    message_text = "AI Kids Care alert test message."

    output_csv = Path(__file__).resolve().parent / "sms_send_results.csv"

    service = build_message_service(api_key=api_key, api_secret=api_secret)

    results = send_sms_batch(
        message_service=service,
        sender=sender,
        recipients=recipients,
        text=message_text,
    )

    print_results(results)
    save_results_csv(results, output_csv=output_csv)
