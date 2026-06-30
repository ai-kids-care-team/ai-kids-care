#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Alarm-event ingest parameter builder (closed-loop step ④, correctness fix D4).

Pure business logic with no ML dependencies — safe to import in tests without stubs.

The single source of truth for turning a captured *alarm-onset* wall-clock instant into the
parameters the backend ingest needs (``dedupKey`` + the event time window). It exists so the
``dedup_key``/time-window correctness can be unit-tested in isolation, independent of the
PyAV/torch decode loop in ``scripts/stream_live_alert_service.py``.

Correctness contract (design D4):
- ``dedupKey`` and ``startTime`` derive from the **captured alarm-onset instant**, NOT the
  event-submission time. A debounce / in-process re-trigger of the *same* alarm within its window
  therefore yields the same ``dedupKey`` and the backend deduplicates on
  ``(kindergarten_id, dedup_key)`` instead of writing a duplicate row.
- ``endTime`` is the alarm window's end (the transition/evaluation instant), so the event carries a
  real, non-zero time window rather than a zero-duration record stamped at submission time.
- The dedup-key string contract (``"{streamId}-{epochSec}"``, second precision) is unchanged; only
  the instant fed into it is corrected.
"""

from __future__ import annotations

from datetime import datetime

from ai_app.utils import backend_ingest
from ai_app.utils.event_type_mapper import map_label


def build_alarm_event_params(
    *,
    stream_id,
    target_label: str,
    target_prob: float,
    alarm_onset: datetime,
    window_end: datetime,
) -> dict:
    """Build the stable ingest parameters for an ``alarm_on`` transition.

    Args:
        stream_id: The camera stream id (used only for the dedup key prefix).
        target_label: The model's target label (e.g. ``"assault"``), mapped to an ``EventTypeEnum``.
        target_prob: The target-class softmax probability driving this alarm (0..1).
        alarm_onset: Wall-clock instant the alarm window *began* (captured at the transition). Drives
            both ``startTime`` and the ``dedupKey`` so the key is stable across a debounce/re-trigger
            of the same alarm.
        window_end: Wall-clock instant the alarm window *ended* (the transition/evaluation instant),
            used for ``endTime``.

    Returns:
        A dict with keys ``event_type``, ``severity``, ``confidence``, ``start_time``, ``end_time``,
        ``dedup_key`` — the positional/keyword arguments ``backend_ingest.submit_event`` consumes.
    """
    onset_epoch = alarm_onset.timestamp()
    return {
        "event_type": map_label(target_label),
        "severity": backend_ingest.severity_from_confidence(target_prob),
        "confidence": float(target_prob),
        "start_time": alarm_onset.isoformat(),
        "end_time": window_end.isoformat(),
        "dedup_key": backend_ingest.build_dedup_key(stream_id, onset_epoch),
    }
