"""
ARC-02: ``ai_app.supervisor`` must load the live alert service via a normal package
import from *inside* a function (child-process entrypoint), never via importlib
file-path loading, and never at module import time (the ML-heavy import stays lazy).

This purges any previously-imported ai_app.* modules and re-imports ai_app.supervisor
cleanly, then asserts ai_app.live.alert_service was NOT pulled in as a side effect.
"""
from __future__ import annotations

import inspect
import os as _os
import sys

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)


def _purge_ai_app_modules() -> None:
    for _pkg in list(sys.modules.keys()):
        if _pkg == "ai_app" or _pkg.startswith("ai_app."):
            del sys.modules[_pkg]


def test_import_supervisor_does_not_load_alert_service():
    _purge_ai_app_modules()
    import ai_app.supervisor  # noqa: F401

    assert "ai_app.live.alert_service" not in sys.modules
    assert "ai_app.live" not in sys.modules or not hasattr(
        sys.modules.get("ai_app.live"), "alert_service"
    )


def test_supervisor_has_no_importlib_file_path_loader():
    """The old ``_load_run_stream_service`` importlib-by-path helper must be gone."""
    _purge_ai_app_modules()
    import ai_app.supervisor as supervisor_module

    assert not hasattr(supervisor_module, "_load_run_stream_service")


def test_worker_entry_imports_alert_service_lazily_inside_the_function():
    """``_worker_entry`` must reference ``ai_app.live.alert_service`` only in its body,
    not via any module-level import — i.e. the import statement lives inside the
    function's source, not at module scope."""
    _purge_ai_app_modules()
    import ai_app.supervisor as supervisor_module

    source = inspect.getsource(supervisor_module._worker_entry)
    assert "from ai_app.live.alert_service import run_stream_service" in source

    module_source = inspect.getsource(supervisor_module)
    # The import must not appear outside the function bodies (i.e. at column 0).
    for line in module_source.splitlines():
        if line.startswith("from ai_app.live.alert_service") or line.startswith(
            "import ai_app.live.alert_service"
        ):
            pytest_fail_message = (
                "ai_app.live.alert_service must only be imported inside a function body"
            )
            raise AssertionError(pytest_fail_message)
