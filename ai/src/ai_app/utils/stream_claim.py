#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Claim/Lease HTTP client (shard-live-detection-deployments design D2).

Calls the Java backend internal claim/renew endpoint ``POST /api/v1/internal/streams/claim`` to
learn which camera streams this AI deployment (``DEPLOYMENT_ID``) should currently be running, and
to renew the lease for streams it is already running. The backend is the sole coordinator
(Redis-backed lease table, ``SET NX EX`` + compare-and-renew); this client is pure HTTP business
logic with no ML dependencies, safe to import in tests without stubs (mirrors
``stream_registry.py`` / ``stream_credentials.py`` / ``backend_ingest.py``).

The claim call doubles as a heartbeat: the ``running`` list tells the backend which streams to
renew for this deployment; the response's ``assigned`` list is the *complete* set this deployment
should run this round (renewed + newly claimed). The caller (the supervisor's reconcile loop)
performs a full reconcile against ``assigned`` — a stream that drops out (lease lost / stream
disabled) is not in ``assigned`` and its local worker is stopped.
"""

from __future__ import annotations

from typing import Any, Callable, List, Optional, Sequence


def claim_streams(
    backend_url: str,
    token: str,
    deployment_id: str,
    capacity: int,
    running: Sequence[int],
    *,
    http_post: Optional[Callable[..., Any]] = None,
) -> List[dict]:
    """Claim/renew streams for this deployment; returns the full ``assigned`` stream list.

    Args:
        backend_url: Base URL of the Java backend, e.g. ``http://backend:8080``.
        token: Shared Bearer token for AI service authentication (``AI_SERVICE_TOKEN``).
        deployment_id: This AI stack's lease-ownership identity (``DEPLOYMENT_ID``); every stream
            this call renews or newly claims is leased to this id.
        capacity: Max concurrent workers this deployment can run (``MAX_WORKERS``); bounds how
            many *new* streams the backend will hand out this round.
        running: Stream ids this deployment is currently running, for lease renewal. An empty
            sequence is valid (cold start) — the backend then only tries to claim new capacity.
        http_post: Optional injectable HTTP POST callable with signature
            ``(url, json, headers, timeout) -> Response``. Defaults to ``requests.post`` at call
            time so the module can be imported without ``requests`` being exercised at import
            time.

    Returns:
        The ``assigned`` list from the response body: the complete stream-descriptor set (each at
        least ``{"streamId", "modelId", "kindergartenId"}``) this deployment should run this
        round. The caller performs a full reconcile against it.

    Raises:
        RuntimeError: If the HTTP response status is not 2xx. The message carries the status code
            but never the token value. Callers (the supervisor's ``_desired()``) treat this as
            transient — keep the current worker set, retry next poll — rather than crashing.
    """
    if http_post is None:
        import requests  # lazy import — keeps module importable without requests installed
        http_post = requests.post

    url = f"{backend_url.rstrip('/')}/api/v1/internal/streams/claim"
    headers = {"Authorization": f"Bearer {token}"}
    body = {
        "deploymentId": deployment_id,
        "capacity": int(capacity),
        "running": [int(s) for s in running],
    }

    response = http_post(url, json=body, headers=headers, timeout=10)

    if not (200 <= response.status_code < 300):
        raise RuntimeError(f"Stream claim failed: HTTP {response.status_code}")

    return list(response.json().get("assigned", []))
