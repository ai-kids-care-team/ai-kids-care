#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Stream credential HTTP client for ADR-0026 Phase 3.

Encapsulates the HTTP call to the Java backend credential endpoint and RTSP URL
assembly. Pure business logic with no ML dependencies, safe to import in tests
without stubs.

Security contract:
- ``fetch_stream_credentials`` never logs or prints credentials or tokens.
  Exceptions include the HTTP status code but never the token value.
- ``build_stream_url`` injects user:password into the URL netloc userinfo.
  Callers MUST NOT log the return value of ``build_stream_url``; log only the
  host:port portion extracted via ``urllib.parse``.
"""

from __future__ import annotations

from typing import Any, Callable, Optional
from urllib.parse import urlparse, urlunparse


def fetch_stream_credentials(
    stream_id: str,
    backend_url: str,
    token: str,
    *,
    deployment_id: Optional[str] = None,
    http_get: Optional[Callable[..., Any]] = None,
) -> dict:
    """Call the Java internal credential endpoint and return the credential dict.

    Args:
        stream_id: The camera stream ID to retrieve credentials for.
        backend_url: Base URL of the Java backend, e.g. ``http://backend:8080``.
        token: Shared Bearer token for AI service authentication (``AI_SERVICE_TOKEN``).
        deployment_id: This AI stack's lease-ownership identity (``DEPLOYMENT_ID``). When
            given, sent as the ``X-Deployment-Id`` header so the backend can verify the
            caller currently holds the stream's claim/lease before releasing credentials
            (shard-live-detection-deployments design, defense-in-depth: a caller without a
            valid lease for this stream gets 404). ``None``/empty omits the header entirely
            (backward compatible with callers that predate the lease model).
        http_get: Optional injectable HTTP GET callable with signature
            ``(url, headers, timeout) -> Response``.  Defaults to
            ``requests.get`` at call time so the module can be imported without
            ``requests`` being exercised at import time.

    Returns:
        Parsed JSON dict containing at minimum ``streamId``, ``sourceUrl``,
        ``streamUser``, and ``streamPassword`` as returned by the Java endpoint.

    Raises:
        RuntimeError: If the HTTP response status code is not 2xx.  The message
            includes the status code but never the token value.

    Security note:
        Do not log or print ``streamPassword`` or the return value of
        ``build_stream_url``.
    """
    if http_get is None:
        import requests  # lazy import — keeps module importable without requests installed
        http_get = requests.get

    url = f"{backend_url.rstrip('/')}/api/v1/internal/streams/{stream_id}/credentials"
    headers = {"Authorization": f"Bearer {token}"}
    if deployment_id:
        headers["X-Deployment-Id"] = deployment_id

    response = http_get(url, headers=headers, timeout=10)

    if not (200 <= response.status_code < 300):
        raise RuntimeError(f"Credential fetch failed: HTTP {response.status_code}")

    return response.json()


def build_stream_url(cred: dict) -> str:
    """Assemble the RTSP stream URL, injecting credentials into netloc userinfo when present.

    Args:
        cred: Credential dict as returned by ``fetch_stream_credentials``.
            Expected keys: ``sourceUrl``, ``streamUser``, ``streamPassword``.

    Returns:
        Full stream URL.  If ``streamUser`` and ``streamPassword`` are both
        non-empty, the format is ``scheme://user:password@host:port/path``.
        Otherwise returns ``sourceUrl`` unchanged.

    Raises:
        ValueError: If ``sourceUrl`` is absent or empty in the credential dict.

    Security note:
        The return value contains the plaintext password in the URL userinfo.
        Callers MUST NOT log this value.  To log the target host safely, use
        ``urlparse(cred["sourceUrl"]).hostname`` and ``.port`` — never
        ``.netloc``, which includes userinfo (``user:pass@host:port``).
    """
    source_url: Optional[str] = cred.get("sourceUrl")
    if not source_url:
        raise ValueError("sourceUrl is missing from credential response")

    stream_user: Optional[str] = cred.get("streamUser")
    stream_password: Optional[str] = cred.get("streamPassword")

    if stream_user and stream_password:
        parsed = urlparse(source_url)
        # Rebuild netloc as user:password@host(:port)
        host_part = parsed.hostname or ""
        if parsed.port:
            host_part = f"{host_part}:{parsed.port}"
        new_netloc = f"{stream_user}:{stream_password}@{host_part}"
        new_parts = (
            parsed.scheme,
            new_netloc,
            parsed.path,
            parsed.params,
            parsed.query,
            parsed.fragment,
        )
        return urlunparse(new_parts)

    return source_url
