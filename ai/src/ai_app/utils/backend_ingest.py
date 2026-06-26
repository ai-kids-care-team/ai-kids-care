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

import time
from typing import Any, Callable, Optional

# Bounded-retry defaults (design D3). Total backoff budget ≈ 0.5 + 1.0 + 2.0 = 3.5s before
# giving up; ``sleeper`` is injectable so tests run with zero wait.
DEFAULT_MAX_ATTEMPTS = 3
DEFAULT_BACKOFF_BASE_SEC = 0.5


def _post_with_retry(
    http_post: Callable[..., Any],
    url: str,
    body: dict,
    headers: dict,
    *,
    what: str,
    max_attempts: int,
    backoff_base_sec: float,
    sleeper: Callable[[float], None],
) -> Any:
    """POST with bounded retry + exponential backoff; raise after the last attempt.

    Retries on either a raised exception (e.g. backend unreachable) or a non-2xx status.
    The caller is responsible for translating an exhausted retry into best-effort give-up
    (log + return ``None``) so the stream service never crashes (design D3).

    Raises:
        RuntimeError / the last underlying exception: only after ``max_attempts`` failures.
    """
    attempts = max(1, int(max_attempts))
    last_error: Optional[BaseException] = None
    for attempt in range(1, attempts + 1):
        try:
            response = http_post(url, json=body, headers=headers, timeout=10)
            if 200 <= response.status_code < 300:
                return response
            last_error = RuntimeError(f"{what} failed: HTTP {response.status_code}")
        except Exception as exc:  # noqa: BLE001 — surfaced verbatim after retries
            last_error = exc

        if attempt < attempts:
            # exponential backoff: base, base*2, base*4, ...
            sleeper(float(backoff_base_sec) * (2 ** (attempt - 1)))

    assert last_error is not None  # loop body always sets it on failure
    raise last_error


def create_session(
    stream_id,
    model_id,
    backend_url: str,
    token: str,
    *,
    http_post: Optional[Callable[..., Any]] = None,
    max_attempts: int = DEFAULT_MAX_ATTEMPTS,
    backoff_base_sec: float = DEFAULT_BACKOFF_BASE_SEC,
    sleeper: Optional[Callable[[float], None]] = None,
) -> int:
    """Create a detection session for a stream; returns the backend ``sessionId``.

    POSTs ``{streamId, modelId}`` to ``/api/v1/internal/detection-sessions``. The backend
    resolves ``(kindergarten_id, camera_id)`` from the stream; the AI only sends ``streamId``.

    Transient failures (unreachable backend or non-2xx) are retried ``max_attempts`` times with
    exponential backoff (``sleeper`` injectable for zero-wait tests, design D3).

    Raises:
        RuntimeError: if the final attempt's status is not 2xx (message has the code, never the
            token). The caller (stream service) catches this and skips event ingest this run.
    """
    if http_post is None:
        import requests  # lazy import — keeps module importable without requests installed
        http_post = requests.post
    if sleeper is None:
        sleeper = time.sleep

    url = f"{backend_url.rstrip('/')}/api/v1/internal/detection-sessions"
    headers = {"Authorization": f"Bearer {token}"}
    body = {"streamId": int(stream_id), "modelId": int(model_id)}

    response = _post_with_retry(
        http_post, url, body, headers,
        what="Session ingest", max_attempts=max_attempts,
        backoff_base_sec=backoff_base_sec, sleeper=sleeper,
    )
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
    evidence: Optional[dict] = None,
    http_post: Optional[Callable[..., Any]] = None,
    max_attempts: int = DEFAULT_MAX_ATTEMPTS,
    backoff_base_sec: float = DEFAULT_BACKOFF_BASE_SEC,
    sleeper: Optional[Callable[[float], None]] = None,
) -> dict:
    """Submit a detection event; returns the backend ``{eventId, duplicate}`` dict.

    POSTs the event (mapped ``eventType``, AI-generated ``dedupKey``) to
    ``/api/v1/internal/detection-events``. The backend deduplicates on
    ``(kindergarten_id, dedup_key)``, returning ``duplicate=true`` for a repeat.

    Args:
        start_time/end_time: ISO-8601 strings (backend OffsetDateTime).
        evidence: optional all-or-nothing descriptor ``{uri, hash, type, mimeType}`` for a
            captured clip. When given it is placed under the body's ``evidence`` key; when
            ``None`` the body omits the key entirely (backward compatible with callers that
            never captured a clip — the backend accepts the optional descriptor, design D1).

    Transient failures (unreachable backend or non-2xx) are retried ``max_attempts`` times with
    exponential backoff (``sleeper`` injectable for zero-wait tests, design D3).

    Raises:
        RuntimeError: if the final attempt's status is not 2xx (message has the code, never the
            token). The caller (stream service) catches this so a failed ingest never crashes.
    """
    if http_post is None:
        import requests  # lazy import
        http_post = requests.post
    if sleeper is None:
        sleeper = time.sleep

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
    if evidence is not None:
        body["evidence"] = evidence

    response = _post_with_retry(
        http_post, url, body, headers,
        what="Event ingest", max_attempts=max_attempts,
        backoff_base_sec=backoff_base_sec, sleeper=sleeper,
    )
    return response.json()


def build_dedup_key(stream_id, alarm_onset_epoch) -> str:
    """Idempotency key from stream + alarm-onset time (second precision).

    The same alarm window (or a reconnect/debounce retry of it) yields the same key, so the
    backend deduplicates and does not create a duplicate detection_events row.
    """
    return f"{stream_id}-{int(alarm_onset_epoch)}"


def severity_from_confidence(confidence: float) -> int:
    """Map an alarm confidence (0..1) to a 1..5 severity bucket by a defined rule.

    Bucket rule (design D2) — explicit, operator-interpretable intervals rather than a
    linear ``round``. Monotonically non-decreasing in ``confidence`` and clamped to ``[1, 5]``;
    identical inputs always yield identical outputs.

        confidence < 0.30 → 1
        0.30 ≤ c < 0.50  → 2
        0.50 ≤ c < 0.70  → 3
        0.70 ≤ c < 0.85  → 4
        c ≥ 0.85         → 5

    ``confidence`` outside ``[0, 1]`` is tolerated (clamped at the ends): values below 0.30
    floor at severity 1, values at/above 0.85 cap at severity 5.
    """
    c = float(confidence)
    if c < 0.30:
        return 1
    if c < 0.50:
        return 2
    if c < 0.70:
        return 3
    if c < 0.85:
        return 4
    return 5
