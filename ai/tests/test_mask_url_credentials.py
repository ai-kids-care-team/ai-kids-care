"""
Tests for ``mask_url_credentials`` in stream_live_alert_service (ADR-0026 Phase 3).

This helper masks ``user:password@`` userinfo in URLs before they reach logs or
exception output. Like test_persistence.py, we pre-install sys.modules stubs for
the heavy ML deps so the module is importable in a plain pytest environment.
"""
from __future__ import annotations

import os
import sys
import types
from unittest.mock import MagicMock


# ---------------------------------------------------------------------------
# sys.modules stubs — must be installed before importing the target module
# ---------------------------------------------------------------------------

def _install_stubs() -> None:
    if "av" not in sys.modules:
        sys.modules["av"] = MagicMock()
    if "numpy" not in sys.modules:
        np_stub = types.ModuleType("numpy")
        np_stub.ndarray = object
        np_stub.mean = MagicMock(return_value=0.0)
        np_stub.argmax = MagicMock(return_value=0)
        sys.modules["numpy"] = np_stub
    if "torch" not in sys.modules:
        torch_stub = MagicMock()
        torch_stub.cuda.is_available.return_value = False
        sys.modules["torch"] = torch_stub
    if "torchvision" not in sys.modules:
        sys.modules["torchvision"] = MagicMock()
    if "transformers" not in sys.modules:
        sys.modules["transformers"] = MagicMock()
    for pkg in ("ai_app", "ai_app.utils", "ai_app.utils.pushover", "ai_app.utils.sms"):
        if pkg not in sys.modules:
            sys.modules[pkg] = MagicMock()
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

mask_url_credentials = _mod.mask_url_credentials


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_masks_userinfo_in_rtsp_url():
    masked = mask_url_credentials("rtsp://admin:s3cret@192.168.1.100:554/live")
    assert masked == "rtsp://***:***@192.168.1.100:554/live"
    assert "s3cret" not in masked
    assert "admin" not in masked


def test_masks_userinfo_in_http_url():
    masked = mask_url_credentials("http://user:p%40ss@host:8080/path?q=1")
    assert masked == "http://***:***@host:8080/path?q=1"
    assert "p%40ss" not in masked


def test_url_without_userinfo_unchanged():
    url = "rtsp://192.168.1.101:554/stream"
    assert mask_url_credentials(url) == url


def test_masks_credentials_embedded_in_exception_message():
    # Simulate an exception string that embeds a credentialed URL (the line 546 path).
    msg = "ConnectionError: failed to open rtsp://admin:s3cret@cam.local:554/live after 3 retries"
    masked = mask_url_credentials(msg)
    assert "s3cret" not in masked
    assert "***:***@cam.local:554/live" in masked
