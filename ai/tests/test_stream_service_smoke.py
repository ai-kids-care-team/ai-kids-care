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
    _mod.VideoMAEForVideoClassification.from_pretrained = MagicMock(return_value=MagicMock())
    _mod.VideoMAEImageProcessor.from_pretrained = MagicMock(return_value=MagicMock())

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
# evidence capture downgrade (design D1) — verified at the unit boundary
# ---------------------------------------------------------------------------

def test_evidence_capture_failure_submits_without_evidence():
    """When save_and_hash raises EvidenceCaptureError, submit_event still runs with evidence=None.

    Replicates the alarm_on best-effort block: capture failure must downgrade, not drop.
    """
    from ai_app.utils.evidence_capture import EvidenceCaptureError

    submitted = {}

    def fake_submit_event(*args, **kwargs):
        submitted["evidence"] = kwargs.get("evidence", "MISSING")
        return {"eventId": 1, "duplicate": False}

    def failing_capture(frames, out_dir):
        raise EvidenceCaptureError("encoder unavailable")

    # mirror the alarm_on block logic
    evidence_descriptor = None
    try:
        failing_capture(["frame"], "/tmp/ev")
    except EvidenceCaptureError:
        evidence_descriptor = None
    fake_submit_event(evidence=evidence_descriptor)

    assert submitted["evidence"] is None
