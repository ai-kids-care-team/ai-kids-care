"""
Unit tests for ai_app.utils.stream_claim.claim_streams (shard-live-detection-deployments design D2).

Injectable ``http_post`` — no real network, no ``requests`` import. Mirrors
test_stream_registry.py / test_backend_ingest.py. Asserts the POST path/body/headers, the
``assigned`` extraction, the non-2xx → RuntimeError contract, and that the token is never leaked
in the error message.
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

from ai_app.utils.stream_claim import claim_streams  # noqa: E402


def _resp(status_code, body):
    r = MagicMock()
    r.status_code = status_code
    r.json.return_value = body
    return r


def test_posts_claim_body_and_returns_assigned():
    body = {"assigned": [
        {"streamId": 7, "modelId": 1, "kindergartenId": 10},
        {"streamId": 9, "modelId": 1, "kindergartenId": 11},
    ]}
    mock_post = MagicMock(return_value=_resp(200, body))

    assigned = claim_streams(
        "http://backend:8080", "tok", "dep-a", 8, [7],
        http_post=mock_post,
    )

    assert assigned == body["assigned"]
    args, kwargs = mock_post.call_args
    assert args[0] == "http://backend:8080/api/v1/internal/streams/claim"
    assert kwargs["headers"]["Authorization"] == "Bearer tok"
    assert kwargs["json"] == {"deploymentId": "dep-a", "capacity": 8, "running": [7]}
    assert kwargs["timeout"] == 10


def test_empty_running_is_valid_cold_start():
    mock_post = MagicMock(return_value=_resp(200, {"assigned": []}))
    assigned = claim_streams("http://b", "t", "dep-a", 4, [], http_post=mock_post)
    assert assigned == []
    assert mock_post.call_args[1]["json"]["running"] == []


def test_running_ids_coerced_to_int():
    mock_post = MagicMock(return_value=_resp(200, {"assigned": []}))
    claim_streams("http://b", "t", "dep-a", 4, ["3", 5], http_post=mock_post)
    assert mock_post.call_args[1]["json"]["running"] == [3, 5]


def test_missing_assigned_key_defaults_to_empty_list():
    mock_post = MagicMock(return_value=_resp(200, {}))
    assert claim_streams("http://b", "t", "dep-a", 4, [], http_post=mock_post) == []


def test_non_2xx_raises_without_leaking_token():
    mock_post = MagicMock(return_value=_resp(403, {}))
    with pytest.raises(RuntimeError) as exc:
        claim_streams("http://backend:8080", "super-secret-token", "dep-a", 8, [], http_post=mock_post)
    msg = str(exc.value)
    assert "403" in msg
    assert "super-secret-token" not in msg
