"""
Tests for SEC-C3-01: an over-limit ``Content-Length`` on ``/predict/upload`` must be
rejected with 413 *before* the request body is parsed/spooled — closing the gap left by
SEC-04's streamed check, which only bounds the RAM copy *after* FastAPI/Starlette has
already resolved ``UploadFile`` (spooling the whole multipart body to a temp file first).

The guard only inspects the declared ``Content-Length`` header (no body read), applies
only to ``/predict/upload`` (``/health`` is untouched), and runs ahead of authentication
so an unauthenticated caller cannot force the server to spend bandwidth/disk on a body
that will be rejected anyway. Requests without a ``Content-Length`` (chunked transfer)
fall through unchanged to the existing streamed check in ``_read_upload_within_limit``
(covered by ``test_upload_streaming_size_limit.py``).
"""
from __future__ import annotations

import io
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient

import ai_app.serving.app as app_module
from ai_app.inference.predictor import PredictionResult, PredictionScore
from ai_app.serving.app import _content_length_exceeds_limit, app
from ai_app.serving.deps import get_predictor as _original_get_predictor

_MOCK_PREDICTION = PredictionResult(
    predicted_id=0,
    predicted_label="normal",
    confidence=0.95,
    scores=[PredictionScore(label="normal", probability=0.95)],
)

# Valid MP4 magic bytes: bytes 4-7 == b'ftyp' (total 12+ bytes)
_VALID_MP4_CONTENT = b"\x00\x00\x00\x18ftypisom" + b"\x00" * 100
_AUTH_HEADERS = {"Authorization": "Bearer correct-token"}


def _make_mock_predictor() -> MagicMock:
    mock = MagicMock()
    mock.predict_video.return_value = _MOCK_PREDICTION
    mock.model_dir = "/fake/model"
    mock.device = "cpu"
    mock.num_frames = 16
    mock.sampling_rate = 4
    mock.labels = ["normal", "abnormal"]
    return mock


@pytest.fixture(autouse=True)
def _configure_predictor_and_token(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", "correct-token")
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    yield
    app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# _content_length_exceeds_limit — pure helper, unit-tested directly
# ---------------------------------------------------------------------------


def test_helper_true_when_declared_length_exceeds_limit():
    assert _content_length_exceeds_limit(str(2 * 1024 * 1024), max_upload_mb=1) is True


def test_helper_false_when_declared_length_within_limit():
    assert _content_length_exceeds_limit(str(1 * 1024 * 1024), max_upload_mb=1) is False


def test_helper_false_when_header_absent():
    """No Content-Length (e.g. chunked transfer) fails open to the streamed check."""
    assert _content_length_exceeds_limit(None, max_upload_mb=1) is False


def test_helper_false_when_header_not_numeric():
    assert _content_length_exceeds_limit("not-a-number", max_upload_mb=1) is False


# ---------------------------------------------------------------------------
# Full-app behavior: rejected before the route handler ever runs
# ---------------------------------------------------------------------------


def test_oversized_content_length_rejected_before_form_parsing(monkeypatch):
    """A declared Content-Length over the limit must 413 without ever reaching the
    route handler's body read (_read_upload_within_limit is never called)."""
    monkeypatch.setenv("AI_MAX_UPLOAD_MB", "1")
    calls = []
    original = app_module._read_upload_within_limit

    async def _tracking(*args, **kwargs):
        calls.append(True)
        return await original(*args, **kwargs)

    monkeypatch.setattr(app_module, "_read_upload_within_limit", _tracking)

    large_content = b"\x00" * (2 * 1024 * 1024)  # 2 MB > 1 MB limit
    client = TestClient(app, raise_server_exceptions=False)
    response = client.post(
        "/predict/upload",
        files={"file": ("test.mp4", io.BytesIO(large_content), "video/mp4")},
        data={"top_k": "3"},
        headers=_AUTH_HEADERS,
    )

    assert response.status_code == 413
    assert "MB limit" in response.json()["detail"]
    assert calls == [], "route handler's streamed read must never run for an over-limit declared body"


def test_oversized_content_length_rejected_even_without_auth_header(monkeypatch):
    """The Content-Length guard runs ahead of authentication: an unauthenticated,
    over-limit request gets 413 (not 401) — it never reaches the auth dependency."""
    monkeypatch.setenv("AI_MAX_UPLOAD_MB", "1")
    large_content = b"\x00" * (2 * 1024 * 1024)
    client = TestClient(app, raise_server_exceptions=False)
    response = client.post(
        "/predict/upload",
        files={"file": ("test.mp4", io.BytesIO(large_content), "video/mp4")},
        data={"top_k": "3"},
        # no Authorization header at all
    )

    assert response.status_code == 413


def test_health_endpoint_unaffected_by_upload_guard():
    client = TestClient(app, raise_server_exceptions=False)
    response = client.get("/health")
    assert response.status_code == 200


def test_within_limit_upload_still_passes_auth_and_validation(monkeypatch):
    """A normal small request must still flow through the guard unchanged."""
    monkeypatch.setenv("AI_MAX_UPLOAD_MB", "512")
    client = TestClient(app, raise_server_exceptions=True)
    response = client.post(
        "/predict/upload",
        files={"file": ("clip.mp4", io.BytesIO(_VALID_MP4_CONTENT), "video/mp4")},
        data={"top_k": "3"},
        headers=_AUTH_HEADERS,
    )
    assert response.status_code == 200, response.text
