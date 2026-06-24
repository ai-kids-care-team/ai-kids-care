"""
Smoke tests for scripts/inject_demo_event.py (harden-auth-bootstrap-and-demo D5).

All HTTP is mocked — no real network, no real secrets (uses an obvious test token).
Asserts the script posts a session + event with a valid Bearer token, and that an
invalid-token path is handled (the backend rejects with non-2xx -> no event created,
the client raises, and the script's main() returns a non-zero exit code).
"""

from __future__ import annotations

import os as _os
import sys

# Make ai/src importable for ai_app.utils, and ai/scripts importable for the script module.
_AI_ROOT = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), ".."))
for _p in (_os.path.join(_AI_ROOT, "src"), _os.path.join(_AI_ROOT, "scripts")):
    if _p not in sys.path:
        sys.path.insert(0, _p)

# Remove any MagicMock stubs a prior test may have installed for these pure-HTTP modules.
for _pkg in list(sys.modules.keys()):
    if _pkg in (
        "ai_app",
        "ai_app.utils",
        "ai_app.utils.backend_ingest",
        "ai_app.utils.event_type_mapper",
    ):
        del sys.modules[_pkg]

from unittest.mock import MagicMock

import pytest

import inject_demo_event

# Obvious non-secret test token (never a real credential).
TEST_TOKEN = "test-ai-service-token-not-secret-2026"


def _mock_response(status_code: int, json_body: dict) -> MagicMock:
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_body
    return resp


class TestInjectDemoEvent:
    def test_posts_session_and_event_with_valid_token(self):
        # Two POSTs: session then event.
        mock_post = MagicMock(side_effect=[
            _mock_response(201, {"sessionId": 55}),
            _mock_response(201, {"eventId": 88, "duplicate": False}),
        ])

        result = inject_demo_event.inject_demo_event(
            "http://localhost:8080",
            TEST_TOKEN,
            stream_id=1,
            model_id=1,
            event_type="ASSAULT",
            confidence=0.94,
            onset_epoch=1771981169.0,
            http_post=mock_post,
        )

        assert result == {"sessionId": 55, "eventId": 88, "duplicate": False}
        assert mock_post.call_count == 2

        session_call, event_call = mock_post.call_args_list
        # Session POST.
        assert session_call.args[0] == "http://localhost:8080/api/v1/internal/detection-sessions"
        assert session_call.kwargs["json"] == {"streamId": 1, "modelId": 1}
        assert session_call.kwargs["headers"]["Authorization"] == f"Bearer {TEST_TOKEN}"
        # Event POST.
        assert event_call.args[0] == "http://localhost:8080/api/v1/internal/detection-events"
        body = event_call.kwargs["json"]
        assert body["sessionId"] == 55
        assert body["eventType"] == "ASSAULT"
        assert body["severity"] == 5  # confidence 0.94 -> bucket 5
        assert body["dedupKey"] == "1-1771981169"
        assert event_call.kwargs["headers"]["Authorization"] == f"Bearer {TEST_TOKEN}"

    def test_empty_token_rejected_before_any_post(self):
        mock_post = MagicMock()
        with pytest.raises(ValueError):
            inject_demo_event.inject_demo_event(
                "http://localhost:8080", "", http_post=mock_post,
            )
        mock_post.assert_not_called()

    def test_invalid_token_rejected_by_backend_no_event_created(self):
        # Session POST returns 401 (invalid token) -> client raises after retries; no event POST.
        mock_post = MagicMock(return_value=_mock_response(401, {}))
        with pytest.raises(RuntimeError) as exc:
            inject_demo_event.inject_demo_event(
                "http://localhost:8080",
                "wrong-token-not-secret",
                http_post=mock_post,
            )
        # The session ingest failed; only session attempts were made, no event POST.
        for call in mock_post.call_args_list:
            assert call.args[0].endswith("/detection-sessions")
        # Token value is never leaked in the raised message.
        assert "wrong-token-not-secret" not in str(exc.value)


class TestMainExitCodes:
    def test_main_returns_zero_on_success(self, monkeypatch):
        def fake_inject(*_a, **_k):
            return {"sessionId": 1, "eventId": 2, "duplicate": False}

        monkeypatch.setattr(inject_demo_event, "inject_demo_event", fake_inject)
        rc = inject_demo_event.main([
            "--backend-url", "http://localhost:8080",
            "--token", TEST_TOKEN,
        ])
        assert rc == 0

    def test_main_returns_two_on_missing_token(self, monkeypatch):
        # No --token and no env var -> ValueError -> exit code 2.
        monkeypatch.delenv("AI_SERVICE_TOKEN", raising=False)
        rc = inject_demo_event.main(["--backend-url", "http://localhost:8080"])
        assert rc == 2
