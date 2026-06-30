"""
Unit tests for ai_app.utils.alarm_event.build_alarm_event_params (correctness fix D4).

Pure logic — no ML stubs, no network. Asserts the two correctness fixes:
  ① dedupKey derives from the captured alarm-onset instant (NOT the submission time), so the same
     alarm re-submitted within its window yields the same key (second precision, jitter-stable);
  ② the event carries the real, non-zero alarm time window (startTime=onset, endTime=window end),
     not a zero-duration record stamped at submission time.
"""

from __future__ import annotations

import os as _os
import sys
from datetime import datetime, timedelta, timezone

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

# Drop any MagicMock stubs a prior test (test_persistence.py) may have installed for ai_app.*
for _pkg in list(sys.modules.keys()):
    if _pkg == "ai_app" or _pkg.startswith("ai_app."):
        del sys.modules[_pkg]

from ai_app.utils.alarm_event import build_alarm_event_params  # noqa: E402


def _params(onset: datetime, end: datetime, *, stream_id="7", prob=0.94, label="assault") -> dict:
    return build_alarm_event_params(
        stream_id=stream_id,
        target_label=label,
        target_prob=prob,
        alarm_onset=onset,
        window_end=end,
    )


class TestDedupKeyFromOnset:
    def test_dedup_key_uses_onset_instant_not_submission_time(self):
        onset = datetime(2026, 2, 25, 0, 59, 29, tzinfo=timezone.utc)
        # Same onset, two *different* (later) window ends — as would happen if the same alarm is
        # re-submitted/debounced later in its window — must produce the SAME dedup key.
        p1 = _params(onset, onset + timedelta(seconds=45))
        p2 = _params(onset, onset + timedelta(seconds=145))
        assert p1["dedup_key"] == p2["dedup_key"]
        assert p1["dedup_key"] == f"7-{int(onset.timestamp())}"

    def test_dedup_key_is_second_precision_jitter_stable(self):
        # Two onsets within the same wall-clock second collapse to the same key.
        onset_a = datetime(2026, 2, 25, 0, 59, 29, 200_000, tzinfo=timezone.utc)
        onset_b = datetime(2026, 2, 25, 0, 59, 29, 800_000, tzinfo=timezone.utc)
        pa = _params(onset_a, onset_a + timedelta(seconds=30))
        pb = _params(onset_b, onset_b + timedelta(seconds=30))
        assert pa["dedup_key"] == pb["dedup_key"]

    def test_different_onset_seconds_give_different_keys(self):
        onset = datetime(2026, 2, 25, 0, 59, 29, tzinfo=timezone.utc)
        later = onset + timedelta(seconds=5)
        assert _params(onset, onset + timedelta(seconds=30))["dedup_key"] != \
            _params(later, later + timedelta(seconds=30))["dedup_key"]


class TestRealTimeWindow:
    def test_window_is_non_zero_and_spans_onset_to_end(self):
        onset = datetime(2026, 2, 25, 0, 59, 29, tzinfo=timezone.utc)
        end = onset + timedelta(seconds=45)
        p = _params(onset, end)
        assert p["start_time"] == onset.isoformat()
        assert p["end_time"] == end.isoformat()
        # Not a zero-duration record stamped at the submission instant.
        assert p["start_time"] != p["end_time"]
        assert datetime.fromisoformat(p["end_time"]) > datetime.fromisoformat(p["start_time"])


class TestMapping:
    def test_event_type_severity_confidence_mapped(self):
        onset = datetime(2026, 2, 25, tzinfo=timezone.utc)
        p = _params(onset, onset + timedelta(seconds=10), prob=0.94, label="assault")
        assert p["event_type"] == "ASSAULT"
        assert p["severity"] == 5  # 0.94 ≥ 0.85 bucket
        assert p["confidence"] == 0.94

    def test_unknown_label_maps_to_other(self):
        onset = datetime(2026, 2, 25, tzinfo=timezone.utc)
        p = _params(onset, onset + timedelta(seconds=10), label="running")
        assert p["event_type"] == "OTHER"
