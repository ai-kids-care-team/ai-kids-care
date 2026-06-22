"""
Unit tests for ai_app.utils.stream_credentials.

All HTTP calls are mocked — no real network requests are made.
No ML stubs needed because stream_credentials.py has no ML imports.

sys.path setup: ``src`` is added here so this test module can import the real
``ai_app`` package regardless of whether ``PYTHONPATH=src`` is set externally.
If a previous test (e.g. test_persistence.py) has already registered MagicMock
stubs for ``ai_app`` sub-packages in ``sys.modules``, those are temporarily
removed so the real package is resolved via ``src``.
"""

from __future__ import annotations

import sys
import os as _os

# ---------------------------------------------------------------------------
# Ensure src/ is importable and real ai_app package is used (not MagicMock stub)
# ---------------------------------------------------------------------------
_AI_SRC = _os.path.normpath(
    _os.path.join(_os.path.dirname(__file__), "..", "src")
)
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

# Remove any MagicMock stubs for ai_app.utils.* that may have been installed
# by test_persistence.py (which stubs them with ``if pkg not in sys.modules``
# guards). We only clear the specific sub-modules we need; the top-level stubs
# for av/torch/transformers are left intact because stream_credentials has no
# ML imports.
for _pkg in list(sys.modules.keys()):
    if _pkg in ("ai_app", "ai_app.utils", "ai_app.utils.stream_credentials"):
        del sys.modules[_pkg]

from unittest.mock import MagicMock
from urllib.parse import urlparse

import pytest

from ai_app.utils.stream_credentials import build_stream_url, fetch_stream_credentials


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _mock_response(status_code: int, json_body: dict) -> MagicMock:
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_body
    return resp


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestFetchAndBuildWithCredentials:
    """Happy path: 200 response with non-empty user and password."""

    def test_fetch_and_build_with_credentials(self):
        cred_body = {
            "streamId": "cam-001",
            "sourceUrl": "rtsp://192.168.1.100:554/live",
            "streamUser": "admin",
            "streamPassword": "s3cret",
        }
        mock_get = MagicMock(return_value=_mock_response(200, cred_body))

        cred = fetch_stream_credentials(
            "cam-001",
            "http://backend:8080",
            "test-token",
            http_get=mock_get,
        )
        result = build_stream_url(cred)

        # Verify HTTP call
        mock_get.assert_called_once()
        call_args = mock_get.call_args
        assert call_args[0][0] == "http://backend:8080/api/v1/internal/streams/cam-001/credentials"
        assert call_args[1]["headers"]["Authorization"] == "Bearer test-token"

        # Verify URL userinfo injection
        parsed = urlparse(result)
        assert parsed.username == "admin"
        assert parsed.password == "s3cret"
        assert parsed.hostname == "192.168.1.100"
        assert parsed.port == 554
        assert parsed.path == "/live"


class TestBuildWithoutCredentials:
    """Null user/password: sourceUrl should be returned unchanged."""

    def test_build_without_credentials(self):
        cred_body = {
            "streamId": "cam-002",
            "sourceUrl": "rtsp://192.168.1.101:554/stream",
            "streamUser": None,
            "streamPassword": None,
        }
        mock_get = MagicMock(return_value=_mock_response(200, cred_body))

        cred = fetch_stream_credentials(
            "cam-002",
            "http://backend:8080",
            "test-token",
            http_get=mock_get,
        )
        result = build_stream_url(cred)

        # No userinfo should be injected
        parsed = urlparse(result)
        assert parsed.username is None
        assert parsed.password is None
        assert result == "rtsp://192.168.1.101:554/stream"


class TestFallbackBuildWithoutHttpCall:
    """Fallback path: build_stream_url with a pre-built dict (no HTTP call)."""

    def test_stream_id_unset_uses_fallback(self):
        # Simulate the STREAM_URL fallback: caller has a direct URL, no STREAM_ID.
        # build_stream_url should return sourceUrl as-is when user/password absent.
        cred = {
            "sourceUrl": "rtsp://host/live",
            "streamUser": None,
            "streamPassword": None,
        }
        result = build_stream_url(cred)
        assert result == "rtsp://host/live"


class TestNon2xxRaises:
    """Non-2xx status must raise RuntimeError without leaking the token."""

    def test_non_2xx_raises(self):
        mock_get = MagicMock(return_value=_mock_response(401, {}))
        token = "super-secret-token"

        with pytest.raises(RuntimeError) as exc_info:
            fetch_stream_credentials(
                "cam-003",
                "http://backend:8080",
                token,
                http_get=mock_get,
            )

        error_msg = str(exc_info.value)
        assert "401" in error_msg
        # Token must not appear in the error message
        assert token not in error_msg
