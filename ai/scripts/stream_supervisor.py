#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Live-detection supervisor entrypoint (multi-camera, Scheme A).

Thin wrapper that puts ``ai/src`` on ``sys.path`` and delegates to
``ai_app.supervisor.main`` — the long-lived process that enumerates the active streams this AI
deployment is responsible for and runs one detection worker per stream, restarting and reconciling
as the active-stream set changes. Runs ALONGSIDE (not replacing) the FastAPI inference endpoint on
port 8001 (``scripts/serve.py``).

Deploy as its own container/service (see ``ai/docker-compose.yml`` service ``ai-live-supervisor``).
"""
from __future__ import annotations

import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = PROJECT_ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ai_app.supervisor import main  # noqa: E402

if __name__ == "__main__":
    main()
