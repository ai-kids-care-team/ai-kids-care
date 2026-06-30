"""
Unit tests for ai_app.utils.stream_registry.fetch_active_streams (Scheme A).

Injectable ``http_get`` — no real network, no ``requests`` import. Mirrors test_stream_credentials.py.
Asserts the GET path/headers, the non-2xx → RuntimeError contract, and that the token is never
leaked in the error message.
"""

from __future__ import annotations

import os as _os
import sys

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

for _pkg in list(sys.modules.keys()):
    if _pkg == "ai_app" or _pkg.startswith("ai_app."):
        del sys.modules[_pkg]

from unittest.mock import MagicMock  # noqa: E402

import pytest  # noqa: E402

from ai_app.utils.stream_registry import fetch_active_streams  # noqa: E402


def _resp(status_code, body):
    r = MagicMock()
    r.status_code = status_code
    r.json.return_value = body
    return r


def test_returns_active_streams_and_calls_correct_endpoint():
    body = [
        {"streamId": 7, "modelId": 1, "kindergartenId": 10},
        {"streamId": 9, "modelId": 1, "kindergartenId": 11},
    ]
    mock_get = MagicMock(return_value=_resp(200, body))
    streams = fetch_active_streams("http://backend:8080", "tok", http_get=mock_get)

    assert streams == body
    args, kwargs = mock_get.call_args
    assert args[0] == "http://backend:8080/api/v1/internal/streams"
    assert kwargs["headers"]["Authorization"] == "Bearer tok"


def test_non_2xx_raises_without_leaking_token():
    mock_get = MagicMock(return_value=_resp(403, []))
    with pytest.raises(RuntimeError) as exc:
        fetch_active_streams("http://backend:8080", "super-secret-token", http_get=mock_get)
    msg = str(exc.value)
    assert "403" in msg
    assert "super-secret-token" not in msg


def test_empty_list_is_valid():
    mock_get = MagicMock(return_value=_resp(200, []))
    assert fetch_active_streams("http://b", "t", http_get=mock_get) == []
