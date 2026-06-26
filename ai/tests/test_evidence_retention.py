"""
Unit tests for the evidence retention / cleanup mechanism added to
ai_app.utils.evidence_capture (follow-up: prevent unbounded disk growth).

Design contract under test:
- Time-based: files older than max_age_days are deleted; files within retention window kept.
- Size-based: when directory total exceeds max_dir_mb, oldest files are deleted (LRU) until
  back under the limit; newest files are kept.
- Combined: both policies run; a file removed by time-pass is simply absent from size-pass.
- Cleanup failure is swallowed: exceptions from unlink/stat are caught and never propagate.
- save_and_hash auto-triggers cleanup after a successful capture (best-effort).
- Non-existent directory: silently returns without error.

All tests use injectable ``clock`` + temp dirs — no real filesystem clock dependency.
"""

from __future__ import annotations

import os as _os
import sys
import time as _real_time

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

# Remove any previously cached module so imports are fresh.
for _pkg in list(sys.modules.keys()):
    if _pkg in (
        "ai_app",
        "ai_app.utils",
        "ai_app.utils.evidence_capture",
    ):
        del sys.modules[_pkg]

import pytest
from pathlib import Path

from ai_app.utils.evidence_capture import (
    EvidenceCaptureError,
    cleanup_evidence_dir,
    save_and_hash,
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_NOW = 1_000_000.0  # arbitrary fixed "now" for all clock mocks


def _fake_encoder(frames, path):
    """Write deterministic bytes so that identical frames → identical file."""
    payload = b"".join(
        bytes(f) if isinstance(f, (bytes, bytearray)) else repr(f).encode()
        for f in frames
    )
    with open(path, "wb") as fh:
        fh.write(b"FAKEMP4" + payload)


def _make_evidence_file(dir_path: Path, name: str, age_seconds: float, size_bytes: int) -> Path:
    """Create a fake evidence file with a controlled mtime and size."""
    p = dir_path / name
    p.write_bytes(b"X" * size_bytes)
    mtime = _NOW - age_seconds
    _os.utime(str(p), (mtime, mtime))
    return p


def _fixed_clock() -> float:
    return _NOW


# ---------------------------------------------------------------------------
# Tests: cleanup_evidence_dir — time-based eviction
# ---------------------------------------------------------------------------

class TestTimeBasedEviction:
    def test_old_file_deleted(self, tmp_path):
        """File older than max_age_days must be removed."""
        old = _make_evidence_file(tmp_path, "evidence_old.mp4", age_seconds=8 * 86400, size_bytes=10)
        cleanup_evidence_dir(str(tmp_path), max_age_days=7, max_dir_mb=0, clock=_fixed_clock)
        assert not old.exists(), "stale file should have been deleted"

    def test_recent_file_kept(self, tmp_path):
        """File younger than max_age_days must not be touched."""
        recent = _make_evidence_file(tmp_path, "evidence_recent.mp4", age_seconds=3 * 86400, size_bytes=10)
        cleanup_evidence_dir(str(tmp_path), max_age_days=7, max_dir_mb=0, clock=_fixed_clock)
        assert recent.exists(), "recent file must not be deleted"

    def test_exactly_at_boundary_kept(self, tmp_path):
        """File whose mtime equals exactly max_age_days ago is kept (boundary is exclusive)."""
        boundary = _make_evidence_file(
            tmp_path, "evidence_boundary.mp4",
            age_seconds=7 * 86400,  # exactly 7 days old
            size_bytes=10,
        )
        cleanup_evidence_dir(str(tmp_path), max_age_days=7, max_dir_mb=0, clock=_fixed_clock)
        # mtime == cutoff means NOT strictly less-than → kept
        assert boundary.exists(), "file at exact boundary age must not be deleted"

    def test_mix_old_and_recent(self, tmp_path):
        """Only files exceeding max_age_days are deleted; recent ones survive."""
        old = _make_evidence_file(tmp_path, "evidence_a.mp4", age_seconds=10 * 86400, size_bytes=10)
        recent = _make_evidence_file(tmp_path, "evidence_b.mp4", age_seconds=1 * 86400, size_bytes=10)
        cleanup_evidence_dir(str(tmp_path), max_age_days=7, max_dir_mb=0, clock=_fixed_clock)
        assert not old.exists()
        assert recent.exists()

    def test_disabled_when_max_age_zero(self, tmp_path):
        """max_age_days <= 0 disables time-based eviction."""
        ancient = _make_evidence_file(tmp_path, "evidence_ancient.mp4", age_seconds=365 * 86400, size_bytes=10)
        cleanup_evidence_dir(str(tmp_path), max_age_days=0, max_dir_mb=0, clock=_fixed_clock)
        assert ancient.exists(), "time eviction disabled — ancient file must not be deleted"


# ---------------------------------------------------------------------------
# Tests: cleanup_evidence_dir — size-based eviction
# ---------------------------------------------------------------------------

class TestSizeBasedEviction:
    def test_oldest_deleted_when_over_limit(self, tmp_path):
        """When total > max_dir_mb, oldest files are deleted until under the limit."""
        # 3 files of 200 KB each = 600 KB total; limit = 400 KB = ~0.39 MB → delete 1
        oldest = _make_evidence_file(tmp_path, "evidence_1.mp4", age_seconds=300, size_bytes=200 * 1024)
        middle = _make_evidence_file(tmp_path, "evidence_2.mp4", age_seconds=200, size_bytes=200 * 1024)
        newest = _make_evidence_file(tmp_path, "evidence_3.mp4", age_seconds=100, size_bytes=200 * 1024)
        cleanup_evidence_dir(
            str(tmp_path), max_age_days=0, max_dir_mb=0.39, clock=_fixed_clock
        )
        assert not oldest.exists(), "oldest file must be evicted first"
        assert newest.exists(), "newest file must survive"

    def test_no_deletion_when_under_limit(self, tmp_path):
        """When total < max_dir_mb all files survive."""
        f1 = _make_evidence_file(tmp_path, "evidence_1.mp4", age_seconds=200, size_bytes=10)
        f2 = _make_evidence_file(tmp_path, "evidence_2.mp4", age_seconds=100, size_bytes=10)
        cleanup_evidence_dir(
            str(tmp_path), max_age_days=0, max_dir_mb=500, clock=_fixed_clock
        )
        assert f1.exists()
        assert f2.exists()

    def test_disabled_when_max_dir_mb_zero(self, tmp_path):
        """max_dir_mb <= 0 disables size-based eviction."""
        big = _make_evidence_file(tmp_path, "evidence_big.mp4", age_seconds=10, size_bytes=1024 * 1024)
        cleanup_evidence_dir(
            str(tmp_path), max_age_days=0, max_dir_mb=0, clock=_fixed_clock
        )
        assert big.exists(), "size eviction disabled — file must not be deleted"

    def test_multiple_oldest_deleted_to_reach_limit(self, tmp_path):
        """If one deletion is insufficient, keeps deleting until under the limit."""
        # 5 files × 100 KB = 500 KB; limit = 150 KB → must delete 4 oldest
        files = []
        for i in range(5):
            f = _make_evidence_file(
                tmp_path, f"evidence_{i+1}.mp4",
                age_seconds=(5 - i) * 100,  # file 1 is oldest
                size_bytes=100 * 1024,
            )
            files.append(f)
        cleanup_evidence_dir(
            str(tmp_path), max_age_days=0, max_dir_mb=0.15, clock=_fixed_clock
        )
        surviving = [f for f in files if f.exists()]
        assert len(surviving) == 1, f"expected 1 survivor, got {len(surviving)}"
        # The newest (index 4) should survive
        assert files[4].exists()


# ---------------------------------------------------------------------------
# Tests: cleanup_evidence_dir — combined policies
# ---------------------------------------------------------------------------

class TestCombinedPolicies:
    def test_time_and_size_both_applied(self, tmp_path):
        """Time-based removes ancient file; size-based removes excess on top."""
        ancient = _make_evidence_file(tmp_path, "evidence_ancient.mp4", age_seconds=10 * 86400, size_bytes=100 * 1024)
        old_big = _make_evidence_file(tmp_path, "evidence_old.mp4", age_seconds=300, size_bytes=300 * 1024)
        new_small = _make_evidence_file(tmp_path, "evidence_new.mp4", age_seconds=10, size_bytes=10 * 1024)
        cleanup_evidence_dir(
            str(tmp_path), max_age_days=7, max_dir_mb=0.05, clock=_fixed_clock
        )
        assert not ancient.exists(), "ancient file deleted by time-based pass"
        # old_big + new_small = ~310 KB > 50 KB limit → old_big also removed
        assert not old_big.exists(), "old_big evicted by size-based pass"
        assert new_small.exists(), "new_small is under limit and recent — must survive"


# ---------------------------------------------------------------------------
# Tests: cleanup_evidence_dir — edge cases and safety
# ---------------------------------------------------------------------------

class TestCleanupEdgeCases:
    def test_nonexistent_dir_does_not_raise(self):
        """Cleanup on a non-existent directory must silently return."""
        cleanup_evidence_dir("/nonexistent/path/that/does/not/exist",
                             max_age_days=7, max_dir_mb=500, clock=_fixed_clock)

    def test_cleanup_exception_swallowed(self, tmp_path):
        """If the listdir callable raises, cleanup_evidence_dir must not propagate."""
        def _boom(path):
            raise RuntimeError("disk exploded")

        # Must not raise:
        cleanup_evidence_dir(
            str(tmp_path), max_age_days=7, max_dir_mb=500,
            clock=_fixed_clock, listdir=_boom,
        )

    def test_unlink_failure_swallowed(self, tmp_path, monkeypatch):
        """If unlink fails for one file, cleanup continues and does not raise."""
        old = _make_evidence_file(tmp_path, "evidence_old.mp4", age_seconds=8 * 86400, size_bytes=10)

        original_unlink = Path.unlink

        def _fail_unlink(self, missing_ok=False):
            raise PermissionError("locked")

        monkeypatch.setattr(Path, "unlink", _fail_unlink)
        # Must not raise even though unlink explodes:
        cleanup_evidence_dir(str(tmp_path), max_age_days=7, max_dir_mb=0, clock=_fixed_clock)

    def test_empty_dir_does_not_raise(self, tmp_path):
        """Empty evidence directory: no files to delete, no crash."""
        cleanup_evidence_dir(str(tmp_path), max_age_days=7, max_dir_mb=500, clock=_fixed_clock)

    def test_non_evidence_files_ignored(self, tmp_path):
        """Files not matching evidence_*.mp4 are not touched."""
        unrelated = tmp_path / "other_file.mp4"
        unrelated.write_bytes(b"data")
        mtime = _NOW - 365 * 86400
        _os.utime(str(unrelated), (mtime, mtime))
        cleanup_evidence_dir(str(tmp_path), max_age_days=1, max_dir_mb=0, clock=_fixed_clock)
        assert unrelated.exists(), "non-evidence file must not be deleted"


# ---------------------------------------------------------------------------
# Tests: save_and_hash auto-triggers cleanup
# ---------------------------------------------------------------------------

class TestSaveAndHashTriggersCleanup:
    def test_cleanup_triggered_after_capture(self, tmp_path):
        """save_and_hash must auto-cleanup stale files after a successful capture."""
        old = _make_evidence_file(tmp_path, "evidence_stale.mp4", age_seconds=8 * 86400, size_bytes=10)
        frames = [b"\x01\x02"]
        save_and_hash(
            frames, str(tmp_path),
            encoder=_fake_encoder,
            max_age_days=7, max_dir_mb=0,
            clock=_fixed_clock,
        )
        assert not old.exists(), "stale file should have been removed by auto-cleanup"

    def test_cleanup_failure_does_not_affect_capture_result(self, tmp_path):
        """If cleanup raises, save_and_hash must still return a valid (uri, hash)."""
        def _boom(path):
            raise RuntimeError("cleanup exploded")

        frames = [b"\x01"]
        uri, digest = save_and_hash(
            frames, str(tmp_path),
            encoder=_fake_encoder,
            max_age_days=7, max_dir_mb=500,
            clock=_fixed_clock,
            _listdir=_boom,
        )
        assert uri.startswith("file://")
        assert len(digest) == 64  # SHA-256 hex = 64 chars

    def test_recent_file_not_deleted_on_auto_cleanup(self, tmp_path):
        """Files within retention window must survive the auto-cleanup triggered by save_and_hash."""
        recent = _make_evidence_file(tmp_path, "evidence_recent.mp4", age_seconds=1 * 86400, size_bytes=10)
        frames = [b"\xAA"]
        save_and_hash(
            frames, str(tmp_path),
            encoder=_fake_encoder,
            max_age_days=7, max_dir_mb=0,
            clock=_fixed_clock,
        )
        assert recent.exists(), "recent file must not be removed by auto-cleanup"
