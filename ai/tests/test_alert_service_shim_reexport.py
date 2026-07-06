"""
ARC-02: ``scripts/stream_live_alert_service.py`` is now a thin shim re-exporting from
the in-package ``ai_app.live.alert_service`` module. This test loads both the shim
(by file path, the same way it is invoked as ``python scripts/stream_live_alert_service.py``)
and the package module (by normal import) and asserts the shim's public symbols are the
*same objects* as the package's — i.e. a re-export, not a parallel re-implementation —
so the legacy entrypoint stays behaviorally equivalent (spec: "Legacy script entrypoint
still resolves").

Heavy ML deps (av/torch/transformers) are stubbed the same way the other
stream_live_alert_service-adjacent tests do it (test_mask_url_credentials.py,
test_persistence.py, test_stream_service_smoke.py).
"""
from __future__ import annotations

import importlib
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

_SHIM_PATH = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "scripts", "stream_live_alert_service.py")
)
_spec = importlib.util.spec_from_file_location("stream_live_alert_service_shim_test", _SHIM_PATH)
_shim = importlib.util.module_from_spec(_spec)
# Register in sys.modules before exec so @dataclass can resolve cls.__module__ (same
# requirement as the existing test_persistence.py / test_mask_url_credentials.py pattern).
sys.modules[_spec.name] = _shim
_spec.loader.exec_module(_shim)  # type: ignore[union-attr]

_package_module = importlib.import_module("ai_app.live.alert_service")


def test_shim_reexports_run_stream_service_identity():
    assert _shim.run_stream_service is _package_module.run_stream_service


def test_shim_reexports_persistence_state_identity():
    assert _shim.PersistenceState is _package_module.PersistenceState
    assert _shim.update_persistence_state is _package_module.update_persistence_state


def test_shim_reexports_mask_url_credentials_identity():
    assert _shim.mask_url_credentials is _package_module.mask_url_credentials


def test_shim_exposes_main_entrypoint():
    assert hasattr(_shim, "main")
    assert callable(_shim.main)
