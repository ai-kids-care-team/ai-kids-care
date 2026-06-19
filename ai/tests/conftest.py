"""
conftest.py — install lightweight stubs for heavy ML dependencies that are
not available on the CI/test host (torch, transformers, av).

numpy is a real dev dependency (see pyproject.toml [dependency-groups] dev)
and must NOT be stubbed here — stubbing it causes MagicMock returns from
np.arange/linspace/clip/round which break test_sample_frame_indices.

These stubs are inserted into sys.modules BEFORE any ai_app module is imported,
so the serving layer can be tested without GPU or large model packages.
"""
from __future__ import annotations

import sys
from unittest.mock import MagicMock

_HEAVY_DEPS = [
    "torch",
    "torch.cuda",
    "transformers",
    "av",
]


def _ensure_stub(name: str) -> None:
    """Insert a MagicMock-backed stub for name and all ancestor packages if absent."""
    parts = name.split(".")
    for i in range(1, len(parts) + 1):
        full = ".".join(parts[:i])
        if full not in sys.modules:
            try:
                __import__(full)
            except ImportError:
                sys.modules[full] = MagicMock(name=full)


for _mod in _HEAVY_DEPS:
    _ensure_stub(_mod)

# Ensure torch.cuda.is_available() is callable and returns False
_torch_stub = sys.modules.get("torch")
if _torch_stub is not None and isinstance(_torch_stub, MagicMock):
    _cuda_stub = MagicMock(name="torch.cuda")
    _cuda_stub.is_available = MagicMock(return_value=False)
    _torch_stub.cuda = _cuda_stub
    sys.modules["torch.cuda"] = _cuda_stub
