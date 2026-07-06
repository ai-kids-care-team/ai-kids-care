"""
Focused tests for ai_app.serving.app hardening changes.

Dependencies: pytest, httpx (required by FastAPI TestClient).
  pip install pytest httpx
If httpx is not installed, these tests will raise ImportError on collection.
httpx is declared in the dev dependency-group of ai/pyproject.toml.
"""
from __future__ import annotations

import io
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient

from ai_app.serving.app import app
from ai_app.inference.predictor import PredictionResult, PredictionScore
from ai_app.serving.deps import get_predictor as _original_get_predictor

# SEC-03: /predict/upload now requires Authorization: Bearer <AI_INFERENCE_TOKEN>.
# These pre-existing tests are not about auth, so every test in this module gets a
# valid token configured (autouse fixture below) and every request carries the header.
_TEST_TOKEN = "test-inference-token-for-serving-tests"
_AUTH_HEADERS = {"Authorization": f"Bearer {_TEST_TOKEN}"}


@pytest.fixture(autouse=True)
def _inference_token(monkeypatch):
    monkeypatch.setenv("AI_INFERENCE_TOKEN", _TEST_TOKEN)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

_MOCK_PREDICTION = PredictionResult(
    predicted_id=0,
    predicted_label="normal",
    confidence=0.95,
    scores=[PredictionScore(label="normal", probability=0.95)],
)


def _make_mock_predictor() -> MagicMock:
    mock = MagicMock()
    mock.predict_video.return_value = _MOCK_PREDICTION
    mock.model_dir = "/fake/model"
    mock.device = "cpu"
    mock.num_frames = 16
    mock.sampling_rate = 4
    mock.labels = ["normal", "abnormal"]
    return mock


# Valid MP4 magic bytes: bytes 4-7 == b'ftyp' (total 12+ bytes)
_VALID_MP4_CONTENT = b"\x00\x00\x00\x18ftypisom" + b"\x00" * 100


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


def test_predict_path_removed():
    """GET and POST /predict/path must return 404 — route was removed."""
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        assert client.post("/predict/path", json={"video_path": "/tmp/fake.mp4"}).status_code == 404
        assert client.get("/predict/path").status_code == 404
    finally:
        app.dependency_overrides.clear()


def test_upload_file_too_large(monkeypatch):
    """Uploading a file exceeding AI_MAX_UPLOAD_MB must return 413."""
    monkeypatch.setenv("AI_MAX_UPLOAD_MB", "1")
    large_content = b"\x00" * (2 * 1024 * 1024)  # 2 MB > 1 MB limit

    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        response = client.post(
            "/predict/upload",
            files={"file": ("test.mp4", io.BytesIO(large_content), "video/mp4")},
            data={"top_k": "3"},
            headers=_AUTH_HEADERS,
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 413
    assert "MB limit" in response.json()["detail"]


def test_upload_invalid_extension():
    """Uploading a file with an unsupported extension must return 422."""
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        response = client.post(
            "/predict/upload",
            files={"file": ("malware.exe", io.BytesIO(b"MZ" + b"\x00" * 100), "application/octet-stream")},
            data={"top_k": "3"},
            headers=_AUTH_HEADERS,
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 422
    assert "Unsupported file type" in response.json()["detail"]


def test_upload_invalid_magic_bytes():
    """Uploading a .mp4 file with non-video content must return 422 (magic-byte check)."""
    fake_content = b"\xFF\xFE" + b"\x00" * 110  # random bytes, no video signature

    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        response = client.post(
            "/predict/upload",
            files={"file": ("disguised.mp4", io.BytesIO(fake_content), "video/mp4")},
            data={"top_k": "3"},
            headers=_AUTH_HEADERS,
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 422
    assert "video container" in response.json()["detail"]


def test_upload_valid_mp4_mock():
    """A valid MP4 magic-byte payload with mocked predictor must return 200 with expected fields."""
    mock_predictor = _make_mock_predictor()

    app.dependency_overrides[_original_get_predictor] = lambda: mock_predictor
    try:
        client = TestClient(app, raise_server_exceptions=True)
        response = client.post(
            "/predict/upload",
            files={"file": ("clip.mp4", io.BytesIO(_VALID_MP4_CONTENT), "video/mp4")},
            data={"top_k": "3"},
            headers=_AUTH_HEADERS,
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200, response.text
    body = response.json()
    assert "predicted_id" in body
    assert "predicted_label" in body
    assert "confidence" in body
    assert "scores" in body
    assert body["predicted_id"] == 0
    assert body["predicted_label"] == "normal"
    assert abs(body["confidence"] - 0.95) < 1e-6


def test_upload_top_k_out_of_range():
    """top_k=0, top_k=51, and top_k=-1 must return 422 (Form range validation)."""
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        for bad_top_k in [0, 51, -1]:
            response = client.post(
                "/predict/upload",
                files={"file": ("clip.mp4", io.BytesIO(_VALID_MP4_CONTENT), "video/mp4")},
                data={"top_k": str(bad_top_k)},
                headers=_AUTH_HEADERS,
            )
            assert response.status_code == 422, (
                f"top_k={bad_top_k} should return 422, got {response.status_code}"
            )
    finally:
        app.dependency_overrides.clear()


def test_upload_num_frames_out_of_range():
    """num_frames=0 must return 422 (Form range validation)."""
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        response = client.post(
            "/predict/upload",
            files={"file": ("clip.mp4", io.BytesIO(_VALID_MP4_CONTENT), "video/mp4")},
            data={"top_k": "3", "num_frames": "0"},
            headers=_AUTH_HEADERS,
        )
        assert response.status_code == 422, (
            f"num_frames=0 should return 422, got {response.status_code}"
        )
    finally:
        app.dependency_overrides.clear()


def test_upload_sampling_rate_out_of_range():
    """sampling_rate=0 must return 422 (Form range validation)."""
    app.dependency_overrides[_original_get_predictor] = lambda: _make_mock_predictor()
    try:
        client = TestClient(app, raise_server_exceptions=False)
        response = client.post(
            "/predict/upload",
            files={"file": ("clip.mp4", io.BytesIO(_VALID_MP4_CONTENT), "video/mp4")},
            data={"top_k": "3", "sampling_rate": "0"},
            headers=_AUTH_HEADERS,
        )
        assert response.status_code == 422, (
            f"sampling_rate=0 should return 422, got {response.status_code}"
        )
    finally:
        app.dependency_overrides.clear()
