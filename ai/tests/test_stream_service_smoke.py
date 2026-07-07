"""
Smoke + downgrade tests for scripts/stream_live_alert_service.run_stream_service.

Covers two follow-ups (design D4 / D1):
- ``notification_title`` is gone from the signature and the service still constructs/runs
  (no ``NameError`` / no residual reference);
- evidence capture is best-effort — when it fails the event is still submitted with
  ``evidence=None`` rather than dropped.

Heavy ML deps are stubbed in sys.modules before importing the script (same pattern as
test_mask_url_credentials.py). The decode loop is short-circuited by making ``av.open`` raise,
and ``max_runtime_sec`` bounds the run so the reconnect loop exits promptly.
"""

from __future__ import annotations

import inspect
import os
import sys
import types
from unittest.mock import MagicMock


def _install_stubs() -> None:
    if "av" not in sys.modules:
        sys.modules["av"] = MagicMock()
    if "torch" not in sys.modules:
        torch_stub = MagicMock()
        torch_stub.cuda.is_available.return_value = False
        sys.modules["torch"] = torch_stub
    if "torchvision" not in sys.modules:
        sys.modules["torchvision"] = MagicMock()
    if "transformers" not in sys.modules:
        sys.modules["transformers"] = MagicMock()
    if "ai_app.utils.pushover" not in sys.modules:
        pushover_stub = types.ModuleType("ai_app.utils.pushover")
        pushover_stub.send_pushover_notification = MagicMock(return_value=True)
        pushover_stub.send_pushover_notifications = MagicMock(return_value=[])
        sys.modules["ai_app.utils.pushover"] = pushover_stub
    if "ai_app.utils.sms" not in sys.modules:
        sms_stub = types.ModuleType("ai_app.utils.sms")
        sms_stub.build_message_service = MagicMock()
        sms_stub.parse_recipients = MagicMock(return_value=[])
        sms_stub.send_sms_batch = MagicMock(return_value=[])
        sys.modules["ai_app.utils.sms"] = sms_stub
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

import importlib.util  # noqa: E402

if "stream_live_alert_service" in sys.modules:
    _mod = sys.modules["stream_live_alert_service"]
else:
    _SERVICE_PATH = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "scripts", "stream_live_alert_service.py")
    )
    _spec = importlib.util.spec_from_file_location("stream_live_alert_service", _SERVICE_PATH)
    _mod = importlib.util.module_from_spec(_spec)
    sys.modules["stream_live_alert_service"] = _mod
    _spec.loader.exec_module(_mod)  # type: ignore[union-attr]

run_stream_service = _mod.run_stream_service

# ARC-02: the shim re-exports run_stream_service from ai_app.live.alert_service, which is
# where VideoMAEForVideoClassification / VideoMAEImageProcessor actually live (the shim
# module itself does not import them) — monkeypatch the real implementation module.
import ai_app.live.alert_service as _impl  # noqa: E402


# ---------------------------------------------------------------------------
# notification_title removal
# ---------------------------------------------------------------------------

def test_run_stream_service_signature_has_no_notification_title():
    params = inspect.signature(run_stream_service).parameters
    assert "notification_title" not in params


def test_run_stream_service_runs_without_notification_title(tmp_path, monkeypatch):
    """Smoke: construct + run a bounded session with backend ingest disabled.

    av.open raises so each connection attempt fails fast; max_runtime_sec bounds the
    reconnect loop. The point is that the service starts and stops with no NameError
    from a residual notification_title reference.
    """
    model_dir = tmp_path / "model"
    model_dir.mkdir()
    out_dir = tmp_path / "out"

    # model loading goes through the transformers stub; make from_pretrained return a MagicMock.
    _impl.VideoMAEForVideoClassification.from_pretrained = MagicMock(return_value=MagicMock())
    _impl.VideoMAEImageProcessor.from_pretrained = MagicMock(return_value=MagicMock())

    av_stub = sys.modules["av"]
    av_stub.open = MagicMock(side_effect=RuntimeError("no stream in test"))

    # zero reconnect wait + tiny runtime so the loop exits immediately
    run_stream_service(
        stream_url="rtsp://fake/stream",
        model_dir=model_dir,
        output_dir=out_dir,
        reconnect_wait_sec=0.0,
        max_runtime_sec=0.0,
        java_backend_url="",      # backend ingest disabled → no session/event calls
        ai_service_token="",
    )


# ---------------------------------------------------------------------------
# evidence capture downgrade (design D1) — verified against the real production code
# (QLT-04/QLT-05): these tests call ``_impl.submit_alarm_event`` directly — the function
# extracted from run_stream_service's alarm_on block — rather than reimplementing its
# try/except logic inline. A regression in the real downgrade path turns these red.
# ---------------------------------------------------------------------------

def test_evidence_capture_failure_submits_without_evidence(tmp_path, monkeypatch):
    """When save_and_hash raises EvidenceCaptureError, submit_event still runs with evidence=None.

    Uses ``_impl.EvidenceCaptureError`` (the class object already bound in the alert_service
    module's own globals) rather than a fresh ``from ai_app.utils.evidence_capture import ...``
    here — some other test module purges ``ai_app.*`` from ``sys.modules`` mid-suite
    (test_supervisor_lazy_import.py), which would otherwise re-import a distinct class object
    that fails an identity-based ``except`` match inside the already-loaded ``_impl`` module.
    """
    submitted = {}

    def fake_submit_event(*args, **kwargs):
        submitted["args"] = args
        submitted["evidence"] = kwargs.get("evidence", "MISSING")
        return {"eventId": 1, "duplicate": False}

    def failing_capture(frames, out_dir, **kwargs):
        raise _impl.EvidenceCaptureError("encoder unavailable")

    monkeypatch.setattr(_impl.backend_ingest, "submit_event", fake_submit_event)
    monkeypatch.setattr(_impl, "save_and_hash", failing_capture)

    from datetime import datetime, timezone

    alarm_onset = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
    window_end = datetime(2026, 1, 1, 12, 0, 5, tzinfo=timezone.utc)

    _impl.submit_alarm_event(
        1,                      # session_id
        ["frame"],              # window_frames
        tmp_path,               # output_dir
        "cam-1",                # stream_id
        "assault",              # target_label
        0.75,                   # target_prob
        alarm_onset,
        window_end,
        "http://backend",       # java_backend_url
        "test-token",           # ai_service_token
    )

    assert submitted["evidence"] is None


def test_evidence_capture_success_submits_with_evidence(tmp_path, monkeypatch):
    """When save_and_hash succeeds, submit_event is called with the evidence descriptor."""
    submitted = {}

    def fake_submit_event(*args, **kwargs):
        submitted["evidence"] = kwargs.get("evidence", "MISSING")
        return {"eventId": 2, "duplicate": False}

    def fake_capture(frames, out_dir, **kwargs):
        return "file:///tmp/ev/evidence_1.mp4", "deadbeef"

    monkeypatch.setattr(_impl.backend_ingest, "submit_event", fake_submit_event)
    monkeypatch.setattr(_impl, "save_and_hash", fake_capture)

    from datetime import datetime, timezone

    alarm_onset = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
    window_end = datetime(2026, 1, 1, 12, 0, 5, tzinfo=timezone.utc)

    _impl.submit_alarm_event(
        1,
        ["frame"],
        tmp_path,
        "cam-1",
        "assault",
        0.75,
        alarm_onset,
        window_end,
        "http://backend",
        "test-token",
    )

    assert submitted["evidence"] == {
        "uri": "file:///tmp/ev/evidence_1.mp4",
        "hash": "deadbeef",
        "type": "VIDEO",
        "mimeType": "video/mp4",
    }


def test_no_session_skips_submit_event(monkeypatch):
    """When session_id is None, submit_event is never called (best-effort skip, not a crash)."""
    called = {"submit": False}

    def fake_submit_event(*args, **kwargs):
        called["submit"] = True
        return {"eventId": 3, "duplicate": False}

    monkeypatch.setattr(_impl.backend_ingest, "submit_event", fake_submit_event)

    from datetime import datetime, timezone

    alarm_onset = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
    window_end = datetime(2026, 1, 1, 12, 0, 5, tzinfo=timezone.utc)

    _impl.submit_alarm_event(
        None,
        ["frame"],
        None,
        "cam-1",
        "assault",
        0.75,
        alarm_onset,
        window_end,
        "http://backend",
        "test-token",
    )

    assert called["submit"] is False
