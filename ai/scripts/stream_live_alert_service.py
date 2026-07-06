#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Thin shim (ARC-02, harden-ai-serving C3).

The live stream alert service implementation moved into the package
(``ai_app.live.alert_service``) so ``ai_app.supervisor`` can load it via a normal
(lazy, function-scoped) package import instead of ``importlib`` file-path loading. This
script re-exports the public symbols for backward compatibility — existing invocations
of ``python scripts/stream_live_alert_service.py`` (and any external references, e.g.
``ai/docker-compose.yml``'s ``ai-live-supervisor`` command override history) keep working
unchanged — and forwards ``__main__`` execution to the package's ``main()``.
"""
from __future__ import annotations

import sys
from pathlib import Path

# Mirrors the pre-ARC-02 script's own sys.path bootstrap: makes `ai_app` importable when
# this file is executed directly (``python scripts/stream_live_alert_service.py``) without
# PYTHONPATH=src already set.
_SRC_DIR = Path(__file__).resolve().parent.parent / "src"
if str(_SRC_DIR) not in sys.path:
    sys.path.insert(0, str(_SRC_DIR))

from ai_app.live.alert_service import (  # noqa: E402,F401
    PersistenceState,
    detect_black_screen,
    main,
    mask_url_credentials,
    run_stream_service,
    update_persistence_state,
)

if __name__ == "__main__":
    main()
