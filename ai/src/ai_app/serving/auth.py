from __future__ import annotations

import hmac
import os

from fastapi import Header, HTTPException

# SEC-03 (harden-ai-serving, C3): dedicated secret for callers *into* the FastAPI
# inference service. Deliberately distinct from AI_SERVICE_TOKEN (the AI->backend
# ingest direction, ROLE_AI_SERVICE) — reusing that token here would let a
# backend-ingest credential also unlock the inference endpoint, collapsing two
# different trust boundaries into one secret.
_TOKEN_ENV_VAR = "AI_INFERENCE_TOKEN"


def get_inference_token() -> str:
    """Read the required inference bearer token from the environment.

    Fails fast (``RuntimeError``) when unset or blank, mirroring the backend's
    ``@NotBlank``/fail-fast secret-injection convention (CLAUDE.md invariant #5):
    the service must refuse to operate rather than silently serving predictions
    unauthenticated. Called both from the FastAPI ``lifespan`` (so a missing
    token fails the process at startup) and from the per-request auth dependency
    below (defense in depth if that startup check is ever bypassed).
    """
    token = os.getenv(_TOKEN_ENV_VAR, "")
    if not token.strip():
        raise RuntimeError(f"{_TOKEN_ENV_VAR} must be set for the inference service")
    return token


async def require_bearer_token(
    authorization: str | None = Header(default=None),
) -> None:
    """FastAPI dependency enforcing ``Authorization: Bearer <AI_INFERENCE_TOKEN>``.

    Never logs the token value or any part of the Authorization header on
    either the success or the failure path. Uses a constant-time comparison
    (``hmac.compare_digest``) so token verification is not a timing oracle.
    """
    expected = get_inference_token()

    if authorization is None or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or malformed bearer token")

    provided = authorization[len("Bearer ") :]
    # Compare as bytes: hmac.compare_digest on str raises TypeError for non-ASCII
    # input (Starlette decodes headers as latin-1), which would surface as a 500
    # instead of a clean 401. Encoding first keeps the path fail-closed *and* 401.
    if not hmac.compare_digest(provided.encode("utf-8"), expected.encode("utf-8")):
        raise HTTPException(status_code=401, detail="Invalid bearer token")
