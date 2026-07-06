"""
Tests for SEC-03: Bearer authentication on the FastAPI inference service.

``/predict/upload`` (the write/inference-triggering endpoint) must require
``Authorization: Bearer <AI_INFERENCE_TOKEN>``. ``/health`` is a deliberate
exception (see notes in ai_app/serving/auth.py and the harden-ai-serving
proposal) and stays open for liveness probes.

AI_INFERENCE_TOKEN is a distinct secret from AI_SERVICE_TOKEN (the AI->backend
ingest direction) — never reused across the two directions.
"""
from __future__ import annotations

import io

import pytest
from fastapi.testclient import TestClient

from ai_app.inference.predictor import PredictionResult, PredictionScore
from ai_app.serving.app import app
from ai_app.serving.auth import get_inference_token, require_bearer_token
from ai_app.serving.deps import get_predictor as _original_get_predictor

_MOCK_PREDICTION = PredictionResult(
    predicted_id=0,
    predicted_label="normal",
    confidence=0.95,
    scores=[PredictionScore(label="normal", probability=0.95)],
)

# Valid MP4 magic bytes: bytes 4-7 == b'ftyp' (total 12+ bytes)
_VALID_MP4_CONTENT = b"\x00\x00\x00\x18ftypisom" + b"\x00" * 100


def _make_mock_predictor():
    from unittest.mock import MagicMock

    mock = MagicMock()
    mock.predict_video.return_value = _MOCK_PREDICTION
    mock.model_dir = "/fake/model"
    mock.device = "cpu"
    mock.num_frames = 16
    mock.sampling_rate = 4
    mock.labels = ["normal", "abnormal"]
    return mock


@pytest.fixture(autouse=True)
def _configure_predictor_override():
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    yield
    app.dependency_overrides.clear()


def _post_upload(client: TestClient, *, headers: dict[str, str] | None = None):
    return client.post(
        "/predict/upload",
        files={"file": ("clip.mp4", io.BytesIO(_VALID_MP4_CONTENT), "video/mp4")},
        data={"top_k": "3"},
        headers=headers or {},
    )


# ---------------------------------------------------------------------------
# get_inference_token() — fail-fast unit behavior
# ---------------------------------------------------------------------------


def test_get_inference_token_raises_when_unset(monkeypatch):
    monkeypatch.delenv("AI_INFERENCE_TOKEN", raising=False)
    with pytest.raises(RuntimeError, match="AI_INFERENCE_TOKEN"):
        get_inference_token()


def test_get_inference_token_raises_when_blank(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "   ")
    with pytest.raises(RuntimeError, match="AI_INFERENCE_TOKEN"):
        get_inference_token()


def test_get_inference_token_returns_configured_value(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "s3cr3t-token")
    assert get_inference_token() == "s3cr3t-token"


# ---------------------------------------------------------------------------
# /predict/upload — 401 without / with wrong token, 200 with the right one
# ---------------------------------------------------------------------------


def test_upload_without_token_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "correct-token")
    client = TestClient(app, raise_server_exceptions=False)
    response = _post_upload(client)
    assert response.status_code == 401


def test_upload_with_wrong_token_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "correct-token")
    client = TestClient(app, raise_server_exceptions=False)
    response = _post_upload(client, headers={"Authorization": "Bearer wrong-token"})
    assert response.status_code == 401


def test_upload_with_malformed_header_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "correct-token")
    client = TestClient(app, raise_server_exceptions=False)
    response = _post_upload(client, headers={"Authorization": "correct-token"})
    assert response.status_code == 401


def test_upload_with_correct_token_succeeds(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "correct-token")
    client = TestClient(app, raise_server_exceptions=True)
    response = _post_upload(client, headers={"Authorization": "Bearer correct-token"})
    assert response.status_code == 200, response.text


def test_token_value_never_appears_in_error_response(monkeypatch):
    """Defense-in-depth: a 401 response body must not echo the configured secret."""
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "super-secret-value")
    client = TestClient(app, raise_server_exceptions=False)
    response = _post_upload(client, headers={"Authorization": "Bearer wrong-guess"})
    assert response.status_code == 401
    assert "super-secret-value" not in response.text


# ---------------------------------------------------------------------------
# /health stays open (documented trade-off — see auth.py / proposal notes)
# ---------------------------------------------------------------------------


def test_health_does_not_require_authentication(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "correct-token")
    client = TestClient(app, raise_server_exceptions=False)
    response = client.get("/health")
    assert response.status_code == 200


# ---------------------------------------------------------------------------
# require_bearer_token dependency, exercised directly (belt-and-suspenders)
# ---------------------------------------------------------------------------


def test_require_bearer_token_dependency_accepts_matching_token(monkeypatch):
    import asyncio

    monkeypatch.setenv("AI_INFERENCE_TOKEN", "tok-abc")
    asyncio.run(require_bearer_token(authorization="Bearer tok-abc"))  # must not raise
