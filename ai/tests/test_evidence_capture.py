"""
Unit tests for ai_app.utils.evidence_capture (closed-loop step ④ follow-up).

The encoder (real ``av``/ffmpeg path) is dependency-injected, so these tests run with a
fake encoder that writes deterministic bytes — no real ``av`` is exercised. Asserts:
- ``save_and_hash`` returns a ``file://`` URI plus a SHA-256 over the written bytes;
- identical frames → identical hash; different content → different hash;
- an encoder that raises surfaces as a recognizable ``EvidenceCaptureError``.
"""

from __future__ import annotations

import hashlib
import os as _os
import sys

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

# Drop any MagicMock stub a prior test may have installed for this module/package tree.
for _pkg in list(sys.modules.keys()):
    if _pkg in (
        "ai_app",
        "ai_app.utils",
        "ai_app.utils.evidence_capture",
    ):
        del sys.modules[_pkg]

import pytest

from ai_app.utils.evidence_capture import (
    EvidenceCaptureError,
    save_and_hash,
)


def _fake_encoder(frames, path):
    """Write deterministic bytes derived from frames so identical frames → identical file."""
    payload = b"".join(bytes(f) if isinstance(f, (bytes, bytearray)) else repr(f).encode() for f in frames)
    with open(path, "wb") as fh:
        fh.write(b"FAKEMP4" + payload)


class TestSaveAndHash:
    def test_returns_file_uri_and_sha256(self, tmp_path):
        frames = [b"\x01\x02", b"\x03\x04"]
        uri, digest = save_and_hash(frames, str(tmp_path), encoder=_fake_encoder)

        assert uri.startswith("file://")
        # the file actually exists on disk
        local_path = uri[len("file://"):]
        # On POSIX file:// is followed by an absolute path beginning with '/'
        assert _os.path.exists(local_path) or _os.path.exists(local_path.lstrip("/"))

        # hash matches a fresh SHA-256 over the written bytes
        resolved = local_path if _os.path.exists(local_path) else local_path.lstrip("/")
        with open(resolved, "rb") as fh:
            expected = hashlib.sha256(fh.read()).hexdigest()
        assert digest == expected

    def test_identical_frames_same_hash(self, tmp_path):
        frames = [b"\x01", b"\x02", b"\x03"]
        _, h1 = save_and_hash(frames, str(tmp_path / "a"), encoder=_fake_encoder)
        _, h2 = save_and_hash(frames, str(tmp_path / "b"), encoder=_fake_encoder)
        assert h1 == h2

    def test_different_content_different_hash(self, tmp_path):
        _, h1 = save_and_hash([b"\x01"], str(tmp_path / "a"), encoder=_fake_encoder)
        _, h2 = save_and_hash([b"\x99"], str(tmp_path / "b"), encoder=_fake_encoder)
        assert h1 != h2

    def test_encoder_failure_raises_recognizable_error(self, tmp_path):
        def _boom(frames, path):
            raise RuntimeError("ffmpeg blew up")

        with pytest.raises(EvidenceCaptureError):
            save_and_hash([b"\x01"], str(tmp_path), encoder=_boom)

    def test_empty_frames_raises(self, tmp_path):
        with pytest.raises(EvidenceCaptureError):
            save_and_hash([], str(tmp_path), encoder=_fake_encoder)
