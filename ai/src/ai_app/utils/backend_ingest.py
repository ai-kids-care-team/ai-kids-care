#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Backend detection-ingest HTTP client (closed-loop step ④).

Submits detection sessions and events to the Java backend internal ingest endpoints,
replacing the demo Pushover/SMS direct dispatch. The backend is the sole writer of the
detection tables and derives kindergarten/camera/room from the stream. Pure HTTP business
logic with no ML dependencies, safe to import in tests without stubs.

- ``create_session`` / ``submit_event`` take an injectable ``http_post`` callable (defaults
  to ``requests.post`` at call time) so the module is importable and testable without
  ``requests`` being exercised at import time (mirrors ``stream_credentials.py``).
- ``Authorization: Bearer {AI_SERVICE_TOKEN}`` is sent on every call and never logged;
  exceptions include the HTTP status code but never the token value.
"""

from __future__ import annotations

from typing import Any, Callable, Optional


def create_session(
    stream_id,
    model_id,
    backend_url: str,
    token: str,
    *,
    http_post: Optional[Callable[..., Any]] = None,
) -> int:
    """Create a detection session for a stream; returns the backend ``sessionId``.

    POSTs ``{streamId, modelId}`` to ``/api/v1/internal/detection-sessions``. The backend
    resolves ``(kindergarten_id, camera_id)`` from the stream; the AI only sends ``streamId``.

    Raises:
        RuntimeError: if the response status is not 2xx (message has the code, never the token).
    """
    if http_post is None:
        import requests  # lazy import — keeps module importable without requests installed
        http_post = requests.post

    url = f"{backend_url.rstrip('/')}/api/v1/internal/detection-sessions"
    headers = {"Authorization": f"Bearer {token}"}
    body = {"streamId": int(stream_id), "modelId": int(model_id)}

    response = http_post(url, json=body, headers=headers, timeout=10)
    if not (200 <= response.status_code < 300):
        raise RuntimeError(f"Session ingest failed: HTTP {response.status_code}")
    return int(response.json()["sessionId"])


def submit_event(
    session_id,
    event_type: str,
    severity: int,
    confidence: float,
    start_time: str,
    end_time: str,
    dedup_key: str,
    backend_url: str,
    token: str,
    *,
    http_post: Optional[Callable[..., Any]] = None,
) -> dict:
    """Submit a detection event; returns the backend ``{eventId, duplicate}`` dict.

    POSTs the event (mapped ``eventType``, AI-generated ``dedupKey``) to
    ``/api/v1/internal/detection-events``. The backend deduplicates on
    ``(kindergarten_id, dedup_key)``, returning ``duplicate=true`` for a repeat.

    Args:
        start_time/end_time: ISO-8601 strings (backend OffsetDateTime).

    Raises:
        RuntimeError: if the response status is not 2xx (message has the code, never the token).
    """
    if http_post is None:
        import requests  # lazy import
        http_post = requests.post

    url = f"{backend_url.rstrip('/')}/api/v1/internal/detection-events"
    headers = {"Authorization": f"Bearer {token}"}
    body = {
        "sessionId": int(session_id),
        "eventType": event_type,
        "severity": int(severity),
        "confidence": float(confidence),
        "startTime": start_time,
        "endTime": end_time,
        "dedupKey": dedup_key,
    }

    response = http_post(url, json=body, headers=headers, timeout=10)
    if not (200 <= response.status_code < 300):
        raise RuntimeError(f"Event ingest failed: HTTP {response.status_code}")
    return response.json()


def build_dedup_key(stream_id, alarm_onset_epoch) -> str:
    """Idempotency key from stream + alarm-onset time (second precision).

    The same alarm window (or a reconnect/debounce retry of it) yields the same key, so the
    backend deduplicates and does not create a duplicate detection_events row.
    """
    return f"{stream_id}-{int(alarm_onset_epoch)}"


def severity_from_confidence(confidence: float) -> int:
    """Map an inference confidence (0..1) to a 1..5 severity bucket (interim — see design D5)."""
    return max(1, min(5, round(float(confidence) * 5)))
