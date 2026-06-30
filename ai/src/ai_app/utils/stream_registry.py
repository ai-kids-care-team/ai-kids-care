#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Active-stream registry HTTP client (multi-camera supervisor, Scheme A).

Calls the Java backend read-only internal endpoint ``GET /api/v1/internal/streams`` to learn which
camera streams this AI deployment should consume. The backend is the single source of truth
(``camera_streams``); the AI never connects to PostgreSQL and the response carries NO decrypted
credentials (those still come from ``GET /api/v1/internal/streams/{id}/credentials``).

Pure HTTP business logic with no ML dependencies, safe to import in tests without stubs (mirrors
``stream_credentials.py`` / ``backend_ingest.py``): ``http_get`` is injectable and ``requests`` is
imported lazily at call time. The Bearer token is never logged; exceptions carry the HTTP status
code but never the token value.
"""

from __future__ import annotations

from typing import Any, Callable, List, Optional


def fetch_active_streams(
    backend_url: str,
    token: str,
    *,
    http_get: Optional[Callable[..., Any]] = None,
) -> List[dict]:
    """Return the active streams this deployment should consume.

    Args:
        backend_url: Base URL of the Java backend, e.g. ``http://backend:8080``.
        token: Shared Bearer token for AI service authentication (``AI_SERVICE_TOKEN``).
        http_get: Optional injectable HTTP GET callable ``(url, headers, timeout) -> Response``.
            Defaults to ``requests.get`` at call time.

    Returns:
        A list of stream descriptors as returned by the backend, each at least
        ``{"streamId": <int>, "modelId": <int|null>, "kindergartenId": <int>}``.

    Raises:
        RuntimeError: If the HTTP response status is not 2xx. The message carries the status code
            but never the token value.
    """
    if http_get is None:
        import requests  # lazy import — keeps module importable without requests installed
        http_get = requests.get

    url = f"{backend_url.rstrip('/')}/api/v1/internal/streams"
    headers = {"Authorization": f"Bearer {token}"}

    response = http_get(url, headers=headers, timeout=10)

    if not (200 <= response.status_code < 300):
        raise RuntimeError(f"Active-stream list failed: HTTP {response.status_code}")

    return list(response.json())
