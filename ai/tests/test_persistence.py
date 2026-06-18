"""
Focused state-machine tests for update_persistence_state and PersistenceState.

Strategy: pre-inject sys.modules stubs before importing stream_live_alert_service
so these tests run in a plain pytest environment without av/torch/transformers/numpy.
"""
from __future__ import annotations

import importlib
import math
import sys
import types
from collections import deque
from unittest.mock import MagicMock


# ---------------------------------------------------------------------------
# sys.modules stubs — must be installed before the first import of the target
# ---------------------------------------------------------------------------

def _install_stubs() -> None:
    """Inject minimal stubs for all heavy dependencies of stream_live_alert_service."""

    # av
    if "av" not in sys.modules:
        sys.modules["av"] = MagicMock()

    # numpy — stub the parts the module-level code and type hints reference
    if "numpy" not in sys.modules:
        np_stub = types.ModuleType("numpy")
        np_stub.ndarray = object  # used only in type annotation
        np_stub.mean = MagicMock(return_value=0.0)
        np_stub.argmax = MagicMock(return_value=0)
        sys.modules["numpy"] = np_stub

    # torch
    if "torch" not in sys.modules:
        torch_stub = MagicMock()
        torch_stub.cuda.is_available.return_value = False
        sys.modules["torch"] = torch_stub

    # torchvision (may be pulled in transitively)
    if "torchvision" not in sys.modules:
        sys.modules["torchvision"] = MagicMock()

    # transformers
    if "transformers" not in sys.modules:
        sys.modules["transformers"] = MagicMock()

    # ai_app package hierarchy
    for pkg in [
        "ai_app",
        "ai_app.utils",
        "ai_app.utils.pushover",
        "ai_app.utils.sms",
    ]:
        if pkg not in sys.modules:
            sys.modules[pkg] = MagicMock()

    # realtime_persistence_demo — stub the symbols imported at the module level
    if "realtime_persistence_demo" not in sys.modules:
        demo_stub = types.ModuleType("realtime_persistence_demo")
        demo_stub.frame_time_sec = MagicMock(return_value=0.0)
        demo_stub.label_for_id = MagicMock(return_value="normal")
        demo_stub.label_to_id = MagicMock(return_value=0)
        demo_stub.maybe_downscale_frame = MagicMock(side_effect=lambda f, **kw: f)
        demo_stub.resolve_fps = MagicMock(return_value=25.0)
        demo_stub.safe_log_text = MagicMock(side_effect=lambda t: t)
        demo_stub.sample_frame_indices = MagicMock(return_value=[])
        sys.modules["realtime_persistence_demo"] = demo_stub


_install_stubs()

# Now it is safe to import the module under test.
import importlib.util
import os

_SERVICE_PATH = os.path.join(
    os.path.dirname(__file__), "..", "scripts", "stream_live_alert_service.py"
)

_spec = importlib.util.spec_from_file_location(
    "stream_live_alert_service", os.path.abspath(_SERVICE_PATH)
)
_mod = importlib.util.module_from_spec(_spec)
# Register in sys.modules before exec so @dataclass can resolve cls.__module__
sys.modules["stream_live_alert_service"] = _mod
_spec.loader.exec_module(_mod)  # type: ignore[union-attr]

PersistenceState = _mod.PersistenceState
update_persistence_state = _mod.update_persistence_state


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_DEFAULT_KWARGS = dict(
    clip_positive_threshold=0.60,
    persistence_window_sec=60.0,
    persistence_hit_ratio=0.50,
    clear_hit_ratio=0.40,
    min_history_sec=30.0,
    min_hits=8,
)


def _make_state_with_history(
    entries: list[tuple[float, int]],
    alarm_on: bool = False,
    alarm_start_sec: float | None = None,
) -> PersistenceState:
    """Build a PersistenceState with pre-populated history."""
    s = PersistenceState()
    s.history = deque(entries)
    s.alarm_on = alarm_on
    s.alarm_start_sec = alarm_start_sec
    return s


# ---------------------------------------------------------------------------
# Test 1 — alarm_on triggered
# ---------------------------------------------------------------------------

def test_alarm_on_triggered() -> None:
    """
    When history is ready, hit_count >= min_hits, and hit_ratio >= persistence_hit_ratio,
    a subsequent valid hit should fire alarm_on.
    """
    # Build 10 entries spanning 35 s (> min_history_sec=30) with all hits.
    # Timestamps: 0, 3.89, ..., 35  (10 entries, span = 35 s)
    base_ts = 1000.0
    entries = [(base_ts + i * (35.0 / 9), 1) for i in range(10)]
    # All 10 are hits: hit_ratio = 1.0, hit_count = 10 >= min_hits=8
    state = _make_state_with_history(entries)

    call_ts = base_ts + 35.0 + 1.0  # new call inside the 60 s window
    result = update_persistence_state(
        state=state,
        ts_sec=call_ts,
        target_prob=0.90,
        **_DEFAULT_KWARGS,
        window_is_valid=True,
    )

    assert result["event_type"] == "alarm_on", f"expected alarm_on, got {result['event_type']!r}"
    assert state.alarm_on is True
    assert result["alarm_on"] == 1


# ---------------------------------------------------------------------------
# Test 2 — alarm_off triggered by window_is_valid=False
# ---------------------------------------------------------------------------

def test_alarm_off_on_invalid_window() -> None:
    """
    While alarm is on, an invalid window (e.g. black screen) must fire alarm_off.
    """
    base_ts = 2000.0
    entries = [(base_ts + i * 4.0, 1) for i in range(10)]
    state = _make_state_with_history(
        entries, alarm_on=True, alarm_start_sec=base_ts
    )

    call_ts = base_ts + 40.0
    result = update_persistence_state(
        state=state,
        ts_sec=call_ts,
        target_prob=0.0,
        **_DEFAULT_KWARGS,
        window_is_valid=False,
    )

    assert result["event_type"] == "alarm_off", f"expected alarm_off, got {result['event_type']!r}"
    assert state.alarm_on is False
    assert result["alarm_on"] == 0
    assert result["event_duration_sec"] >= 0.0


# ---------------------------------------------------------------------------
# Test 3 — expired entries are removed from history
# ---------------------------------------------------------------------------

def test_expired_history_entries_removed() -> None:
    """
    Entries older than (ts_sec - persistence_window_sec) must be evicted.
    Calling at ts_sec=200 with persistence_window_sec=60 means entries before 140 are evicted.
    """
    call_ts = 200.0
    window = 60.0
    # Two old entries (well before cutoff=140) and one fresh one.
    old1 = (100.0, 1)
    old2 = (110.0, 1)
    fresh = (190.0, 1)

    state = _make_state_with_history([old1, old2, fresh])

    result = update_persistence_state(
        state=state,
        ts_sec=call_ts,
        target_prob=0.90,
        clip_positive_threshold=0.60,
        persistence_window_sec=window,
        persistence_hit_ratio=0.50,
        clear_hit_ratio=0.40,
        min_history_sec=30.0,
        min_hits=8,
        window_is_valid=True,
    )

    # fresh entry remains, the new call_ts entry was appended, old ones evicted
    # rolling_count should be 2 (fresh + the new appended entry at call_ts=200)
    assert result["rolling_count"] == 2, (
        f"expected 2 (fresh + new), got {result['rolling_count']}"
    )


# ---------------------------------------------------------------------------
# Test 4 — window_is_valid=False does not append to history
# ---------------------------------------------------------------------------

def test_invalid_window_does_not_append() -> None:
    """
    When window_is_valid=False, no new entry should be added to history.
    """
    state = PersistenceState()
    # Pre-populate with two entries well inside the window.
    call_ts = 100.0
    state.history = deque([(90.0, 1), (95.0, 1)])
    count_before = len(state.history)

    result = update_persistence_state(
        state=state,
        ts_sec=call_ts,
        target_prob=0.90,
        **_DEFAULT_KWARGS,
        window_is_valid=False,
    )

    assert result["clip_hit"] == 0
    # rolling_count reflects the history after eviction but no append
    assert result["rolling_count"] == count_before, (
        f"expected {count_before}, got {result['rolling_count']} (invalid window should not append)"
    )


# ---------------------------------------------------------------------------
# Test 5 — empty history (first call, no exception)
# ---------------------------------------------------------------------------

def test_empty_history_no_exception() -> None:
    """
    A fresh PersistenceState on the first call must not raise and history_ready must be 0.
    """
    state = PersistenceState()

    result = update_persistence_state(
        state=state,
        ts_sec=0.0,
        target_prob=0.80,
        **_DEFAULT_KWARGS,
        window_is_valid=True,
    )

    # A single entry has span=0, so history_ready must be 0
    assert result["history_ready"] == 0
    # history_count is either 1 (appended) or 0 depending on window_is_valid — here 1
    assert result["rolling_count"] in (0, 1)


def test_empty_history_invalid_window_no_exception() -> None:
    """
    First call with window_is_valid=False on empty history must not raise.
    """
    state = PersistenceState()

    result = update_persistence_state(
        state=state,
        ts_sec=50.0,
        target_prob=0.0,
        **_DEFAULT_KWARGS,
        window_is_valid=False,
    )

    assert result["history_ready"] == 0
    assert result["rolling_count"] == 0


# ---------------------------------------------------------------------------
# Test 6 — history_ready=1 when span exactly meets min_history_sec
# ---------------------------------------------------------------------------

def test_history_ready_at_exact_min_history_sec() -> None:
    """
    When history_span_sec == min_history_sec, history_ready must be 1.
    """
    min_hist = 30.0
    base_ts = 500.0
    # Two entries exactly 30 s apart; span = 30.0 == min_history_sec
    entries = [(base_ts, 1), (base_ts + min_hist, 1)]
    state = _make_state_with_history(entries)

    # Call with a new ts just after, within the 60 s window
    call_ts = base_ts + min_hist + 0.5
    result = update_persistence_state(
        state=state,
        ts_sec=call_ts,
        target_prob=0.90,
        clip_positive_threshold=0.60,
        persistence_window_sec=60.0,
        persistence_hit_ratio=0.50,
        clear_hit_ratio=0.40,
        min_history_sec=min_hist,
        min_hits=8,
        window_is_valid=True,
    )

    assert result["history_ready"] == 1, (
        f"history_span_sec={result['history_span_sec']:.2f} should satisfy min_history_sec={min_hist}"
    )
