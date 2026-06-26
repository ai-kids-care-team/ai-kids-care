"""
Unit tests for ai_app.utils.backend_ingest and event_type_mapper (closed-loop step ④).

All HTTP calls are mocked — no real network requests, no ``requests`` import. No ML stubs
needed (pure HTTP/mapping logic). Mirrors test_stream_credentials.py's injectable-mock style.
"""

from __future__ import annotations

import os as _os
import sys

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

# Remove any MagicMock stubs a prior test (test_persistence.py) may have installed.
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

from ai_app.utils.backend_ingest import (
    build_dedup_key,
    create_session,
    severity_from_confidence,
    submit_event,
)
from ai_app.utils.event_type_mapper import map_label


def _mock_response(status_code: int, json_body: dict) -> MagicMock:
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_body
    return resp


class TestCreateSession:
    def test_posts_session_and_returns_id(self):
        mock_post = MagicMock(return_value=_mock_response(200, {"sessionId": 42}))
        session_id = create_session("7", "1", "http://backend:8080", "tok", http_post=mock_post)

        assert session_id == 42
        args, kwargs = mock_post.call_args
        assert args[0] == "http://backend:8080/api/v1/internal/detection-sessions"
        assert kwargs["json"] == {"streamId": 7, "modelId": 1}
        assert kwargs["headers"]["Authorization"] == "Bearer tok"

    def test_non_2xx_raises_without_leaking_token(self):
        mock_post = MagicMock(return_value=_mock_response(403, {}))
        with pytest.raises(RuntimeError) as exc:
            create_session("7", "1", "http://backend:8080", "super-secret-token", http_post=mock_post)
        msg = str(exc.value)
        assert "403" in msg
        assert "super-secret-token" not in msg


class TestSubmitEvent:
    def test_posts_event_camelcase_and_returns(self):
        mock_post = MagicMock(return_value=_mock_response(201, {"eventId": 9, "duplicate": False}))
        result = submit_event(
            42, "ASSAULT", 4, 0.94,
            "2026-02-25T00:59:29+09:00", "2026-02-25T01:00:00+09:00",
            "7-1771981169", "http://backend:8080", "tok", http_post=mock_post,
        )

        assert result == {"eventId": 9, "duplicate": False}
        args, kwargs = mock_post.call_args
        assert args[0] == "http://backend:8080/api/v1/internal/detection-events"
        body = kwargs["json"]
        assert body["sessionId"] == 42
        assert body["eventType"] == "ASSAULT"
        assert body["severity"] == 4
        assert body["confidence"] == 0.94
        assert body["startTime"] == "2026-02-25T00:59:29+09:00"
        assert body["dedupKey"] == "7-1771981169"
        assert kwargs["headers"]["Authorization"] == "Bearer tok"

    def test_duplicate_response_passthrough(self):
        mock_post = MagicMock(return_value=_mock_response(200, {"eventId": 9, "duplicate": True}))
        result = submit_event(42, "OTHER", 3, 0.5, "s", "e", "k", "http://b", "t", http_post=mock_post)
        assert result["duplicate"] is True

    def test_non_2xx_raises(self):
        # one attempt only → fail fast, no backoff
        mock_post = MagicMock(return_value=_mock_response(500, {}))
        with pytest.raises(RuntimeError):
            submit_event(
                1, "OTHER", 1, 0.1, "s", "e", "k", "http://b", "t",
                http_post=mock_post, max_attempts=1, sleeper=lambda _s: None,
            )

    def test_no_evidence_key_when_evidence_none(self):
        mock_post = MagicMock(return_value=_mock_response(201, {"eventId": 1, "duplicate": False}))
        submit_event(42, "OTHER", 3, 0.5, "s", "e", "k", "http://b", "t", http_post=mock_post)
        body = mock_post.call_args.kwargs["json"]
        assert "evidence" not in body

    def test_evidence_descriptor_included_when_provided(self):
        mock_post = MagicMock(return_value=_mock_response(201, {"eventId": 1, "duplicate": False}))
        descriptor = {
            "uri": "file:///tmp/clip.mp4",
            "hash": "abc123",
            "type": "VIDEO",
            "mimeType": "video/mp4",
        }
        submit_event(
            42, "ASSAULT", 4, 0.94, "s", "e", "k", "http://b", "t",
            evidence=descriptor, http_post=mock_post,
        )
        body = mock_post.call_args.kwargs["json"]
        assert body["evidence"] == descriptor
        assert set(body["evidence"]) == {"uri", "hash", "type", "mimeType"}


class TestIngestRetry:
    """Bounded retry + backoff (design D3); no-op sleeper keeps tests instant."""

    def test_retries_then_succeeds_and_counts_attempts(self):
        # first two attempts non-2xx, third 200 → success after 3 calls
        mock_post = MagicMock(side_effect=[
            _mock_response(503, {}),
            _mock_response(503, {}),
            _mock_response(200, {"eventId": 5, "duplicate": False}),
        ])
        sleeps: list[float] = []
        result = submit_event(
            1, "OTHER", 1, 0.1, "s", "e", "k", "http://b", "t",
            http_post=mock_post, max_attempts=3, sleeper=sleeps.append,
        )
        assert result == {"eventId": 5, "duplicate": False}
        assert mock_post.call_count == 3
        assert len(sleeps) == 2  # backoff between the 3 attempts
        assert sleeps == [0.5, 1.0]  # exponential from default base 0.5

    def test_retries_on_raised_exception_then_succeeds(self):
        mock_post = MagicMock(side_effect=[
            ConnectionError("backend unreachable"),
            _mock_response(201, {"sessionId": 7}),
        ])
        sleeps: list[float] = []
        session_id = create_session(
            "7", "1", "http://b", "t",
            http_post=mock_post, max_attempts=3, sleeper=sleeps.append,
        )
        assert session_id == 7
        assert mock_post.call_count == 2
        assert len(sleeps) == 1

    def test_exhausted_retries_raise_after_max_attempts(self):
        mock_post = MagicMock(return_value=_mock_response(500, {}))
        with pytest.raises(RuntimeError):
            submit_event(
                1, "OTHER", 1, 0.1, "s", "e", "k", "http://b", "t",
                http_post=mock_post, max_attempts=3, sleeper=lambda _s: None,
            )
        assert mock_post.call_count == 3  # all attempts consumed, then raise

    def test_exhausted_retries_propagate_last_exception(self):
        mock_post = MagicMock(side_effect=ConnectionError("down"))
        with pytest.raises(ConnectionError):
            create_session(
                "7", "1", "http://b", "t",
                http_post=mock_post, max_attempts=2, sleeper=lambda _s: None,
            )
        assert mock_post.call_count == 2


class TestEventTypeMapping:
    def test_known_labels(self):
        assert map_label("assault") == "ASSAULT"
        assert map_label("Fight") == "FIGHT"
        assert map_label("  DRUNKEN ") == "DRUNKEN"
        assert map_label("datefight") == "DATEFIGHT"
        assert map_label("kidnap") == "KIDNAP"

    def test_unknown_maps_to_other(self):
        assert map_label("running") == "OTHER"
        assert map_label("") == "OTHER"
        assert map_label(None) == "OTHER"


class TestDedupAndSeverity:
    def test_dedup_key_stable_for_same_alarm_onset(self):
        assert build_dedup_key("7", 1771981169.7) == "7-1771981169"
        # second-precision: jitter within the same second yields the same key
        assert build_dedup_key("7", 1771981169.7) == build_dedup_key("7", 1771981169.2)

    def test_severity_bounds(self):
        assert severity_from_confidence(0.0) == 1
        assert severity_from_confidence(1.0) == 5
        assert severity_from_confidence(0.2) == 1
        assert severity_from_confidence(0.7) == 4


class TestSeverityBuckets:
    """Defined bucket rule (design D2): <0.30→1, <0.50→2, <0.70→3, <0.85→4, ≥0.85→5.

    Asserts the documented mapping, monotonic non-decrease, clamp to [1,5], and
    stability for identical inputs.
    """

    @pytest.mark.parametrize(
        "confidence,expected",
        [
            # below the first boundary → 1
            (-0.5, 1),     # clamped below [0,1] still floors at 1
            (0.0, 1),
            (0.29, 1),
            (0.2999, 1),
            # [0.30, 0.50) → 2
            (0.30, 2),
            (0.40, 2),
            (0.4999, 2),
            # [0.50, 0.70) → 3
            (0.50, 3),
            (0.60, 3),
            (0.6999, 3),
            # [0.70, 0.85) → 4
            (0.70, 4),
            (0.80, 4),
            (0.8499, 4),
            # [0.85, 1.0] → 5
            (0.85, 5),
            (0.94, 5),
            (1.0, 5),
            (1.5, 5),       # clamped above [0,1] still caps at 5
        ],
    )
    def test_bucket_boundaries(self, confidence, expected):
        assert severity_from_confidence(confidence) == expected

    def test_monotonically_non_decreasing(self):
        prev = 0
        c = 0.0
        while c <= 1.0001:
            s = severity_from_confidence(c)
            assert s >= prev, f"severity decreased at confidence={c}"
            assert 1 <= s <= 5
            prev = s
            c += 0.01

    def test_stable_for_identical_inputs(self):
        assert severity_from_confidence(0.6) == severity_from_confidence(0.6)
        assert severity_from_confidence(0.85) == severity_from_confidence(0.85)
