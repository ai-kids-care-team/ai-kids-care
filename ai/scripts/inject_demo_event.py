#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Manual detection-event injection for demonstration (harden-auth-bootstrap-and-demo D5).

DEMO ONLY. This standalone script drives the realtime detection loop
(detection session + event -> SSE dashboard -> review -> guardian notification)
*without* running the AI inference service. It reuses the exact backend internal
ingest client the real AI uses (``ai_app.utils.backend_ingest``), so the path is
identical to a genuine AI ingest:

    POST /api/v1/internal/detection-sessions   {streamId, modelId}
    POST /api/v1/internal/detection-events     {sessionId, eventType, severity, ...}

Authentication is a Bearer ``AI_SERVICE_TOKEN`` (same as the AI service). The
token is read from --token or the ``AI_SERVICE_TOKEN`` env var and is never
printed. Stream/model ids default to the demo seed (stream_id=1, model_id=1 from
db/initdb/39_camera_streams_seed.sql / 40_ai_models_seed.sql), so an injected
event lands on kindergarten 1's dashboard.

WARNING: never run this against production. Production is seed-free and has no
demo stream/model; this tool exists purely to demonstrate the realtime flow on a
demo/local/CI backend. It requires an explicit --backend-url (default localhost)
and an explicit token.

Example (demo backend on localhost):

    AI_SERVICE_TOKEN=... python scripts/inject_demo_event.py \
        --backend-url http://localhost:8080 \
        --stream-id 1 --model-id 1 \
        --event-type ASSAULT --confidence 0.94
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path
from typing import Any, Callable, Optional

_SRC_DIR = Path(__file__).resolve().parent.parent / "src"
if str(_SRC_DIR) not in sys.path:
    sys.path.insert(0, str(_SRC_DIR))
del _SRC_DIR

from ai_app.utils.backend_ingest import (  # noqa: E402  (sys.path set above)
    build_dedup_key,
    create_session,
    severity_from_confidence,
    submit_event,
)
from ai_app.utils.event_type_mapper import map_label  # noqa: E402

# Demo seed defaults — stream/model that exist only in the db/initdb demo seed
# (kindergarten 1). NEVER present in a seed-free production database.
DEFAULT_BACKEND_URL = "http://localhost:8080"
DEFAULT_STREAM_ID = 1
DEFAULT_MODEL_ID = 1
DEFAULT_EVENT_TYPE = "ASSAULT"
DEFAULT_CONFIDENCE = 0.94


def inject_demo_event(
    backend_url: str,
    token: str,
    *,
    stream_id: int = DEFAULT_STREAM_ID,
    model_id: int = DEFAULT_MODEL_ID,
    event_type: str = DEFAULT_EVENT_TYPE,
    confidence: float = DEFAULT_CONFIDENCE,
    onset_epoch: Optional[float] = None,
    http_post: Optional[Callable[..., Any]] = None,
    clock: Callable[[], float] = time.time,
) -> dict:
    """Create one detection session and submit one detection event.

    Reuses the production ingest client so the demo path is byte-for-byte the
    same as a real AI ingest. ``http_post`` is injectable for tests (no network);
    defaults to ``requests.post`` inside the client. The token is sent as a Bearer
    header by the client and is never logged here.

    Returns a dict ``{sessionId, eventId, duplicate}`` describing what the backend
    persisted.

    Raises:
        ValueError: if the token is empty (refuse to call the protected endpoint).
        RuntimeError: if the backend rejects the session/event (e.g. invalid token
            -> the client raises on the non-2xx response; no event is created).
    """
    if not token or not token.strip():
        raise ValueError("An AI_SERVICE_TOKEN is required to call the internal ingest endpoints.")

    onset = float(onset_epoch) if onset_epoch is not None else float(clock())
    # event_type may be a raw model label or an already-mapped enum value; normalise
    # via the same mapper the AI uses (unknown -> OTHER), but pass through values
    # that are already enum names so e.g. "ASSAULT" stays "ASSAULT".
    normalized_type = event_type if event_type == map_label(event_type) else map_label(event_type)
    severity = severity_from_confidence(confidence)
    dedup_key = build_dedup_key(stream_id, onset)

    # ISO-8601 with offset; demo uses the same instant for start/end (single frame window).
    start_iso = _iso8601(onset)
    end_iso = start_iso

    session_id = create_session(
        stream_id, model_id, backend_url, token, http_post=http_post,
    )
    result = submit_event(
        session_id,
        normalized_type,
        severity,
        confidence,
        start_iso,
        end_iso,
        dedup_key,
        backend_url,
        token,
        http_post=http_post,
    )
    return {
        "sessionId": session_id,
        "eventId": result.get("eventId"),
        "duplicate": result.get("duplicate"),
    }


def _iso8601(epoch: float) -> str:
    """Local-time ISO-8601 string with UTC offset (backend OffsetDateTime)."""
    lt = time.localtime(epoch)
    base = time.strftime("%Y-%m-%dT%H:%M:%S", lt)
    # tm_gmtoff is seconds east of UTC; format as +HH:MM.
    offset = lt.tm_gmtoff if lt.tm_gmtoff is not None else 0
    sign = "+" if offset >= 0 else "-"
    offset = abs(offset)
    return f"{base}{sign}{offset // 3600:02d}:{(offset % 3600) // 60:02d}"


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="DEMO-ONLY manual detection-event injection (never run against production).",
    )
    parser.add_argument(
        "--backend-url", default=DEFAULT_BACKEND_URL,
        help=f"Backend base URL (default: {DEFAULT_BACKEND_URL}).",
    )
    parser.add_argument(
        "--token", default=os.environ.get("AI_SERVICE_TOKEN", ""),
        help="AI service Bearer token (default: $AI_SERVICE_TOKEN). Never printed.",
    )
    parser.add_argument(
        "--stream-id", type=int, default=DEFAULT_STREAM_ID,
        help=f"Demo seed stream id (default: {DEFAULT_STREAM_ID}).",
    )
    parser.add_argument(
        "--model-id", type=int, default=DEFAULT_MODEL_ID,
        help=f"Demo seed model id (default: {DEFAULT_MODEL_ID}).",
    )
    parser.add_argument(
        "--event-type", default=DEFAULT_EVENT_TYPE,
        help=f"Event type label or enum (default: {DEFAULT_EVENT_TYPE}).",
    )
    parser.add_argument(
        "--confidence", type=float, default=DEFAULT_CONFIDENCE,
        help=f"Detection confidence 0..1 (default: {DEFAULT_CONFIDENCE}).",
    )
    return parser


def main(argv: Optional[list] = None) -> int:
    args = _build_arg_parser().parse_args(argv)
    try:
        result = inject_demo_event(
            args.backend_url,
            args.token,
            stream_id=args.stream_id,
            model_id=args.model_id,
            event_type=args.event_type,
            confidence=args.confidence,
        )
    except ValueError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 2
    except Exception as exc:  # noqa: BLE001 — surface a clean message; client omits the token
        print(f"[ERROR] injection failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1

    print(
        "Injected demo detection event: "
        f"sessionId={result['sessionId']} eventId={result['eventId']} "
        f"duplicate={result['duplicate']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
