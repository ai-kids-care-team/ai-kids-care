#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Evidence clip capture + content hashing (closed-loop step ④ follow-up, design D1).

On an ``alarm_on`` transition the stream service hands a slice of its RGB frame buffer to
``save_and_hash``, which encodes a short ``video/mp4`` clip to a local path, computes a
SHA-256 over the written bytes, and returns a ``(file:// uri, sha256)`` pair for the
``submit_event`` ``evidence`` descriptor.

Design notes:
- The encoder (the real ``av``/ffmpeg path) is **dependency-injected** via the ``encoder``
  parameter so this module is testable without ``av`` (``conftest.py`` stubs ``av`` as a
  MagicMock; a fake encoder writing deterministic bytes covers the hashing contract).
- Local ``file://`` only this slice — the backend stores the URI string + hash and never reads
  the bytes; remote object storage (``s3://``) is a later, independent evolution.
- Any encoder/IO/hash failure is wrapped in ``EvidenceCaptureError`` so the caller can do a
  clean best-effort downgrade (submit the event *without* evidence rather than drop it).

Retention / cleanup (follow-up: prevent unbounded disk growth):
- ``cleanup_evidence_dir`` is a **best-effort, injectable** cleanup function.
- Two independent eviction policies can be combined:
  - **Time-based**: delete files older than ``max_age_days`` (default 7 days).
  - **Size-based**: when the directory total exceeds ``max_dir_mb`` MB, delete the oldest
    files (by mtime, LRU) until back under the limit.
- Defaults are conservative — 7 days / 500 MB — to avoid silent data loss on first deploy.
- ``clock`` (injectable ``() -> float``) and ``listdir`` (injectable path-listing callable)
  allow deterministic unit tests without touching the real filesystem clock.
- ``save_and_hash`` calls ``cleanup_evidence_dir`` automatically after a successful capture.
  Cleanup failures are caught and logged; they never propagate to the caller.
"""

from __future__ import annotations

import hashlib
import logging
import os
import time
import uuid
from pathlib import Path
from typing import Any, Callable, List, Optional, Sequence, Tuple

_log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration defaults — override via env vars
# ---------------------------------------------------------------------------
_DEFAULT_MAX_AGE_DAYS: float = float(os.environ.get("EVIDENCE_MAX_AGE_DAYS", "7"))
_DEFAULT_MAX_DIR_MB: float = float(os.environ.get("EVIDENCE_MAX_DIR_MB", "500"))


class EvidenceCaptureError(Exception):
    """Raised when clip encoding or hashing fails; signals the caller to downgrade gracefully."""


def _default_encoder(frames: Sequence[Any], path: str) -> None:
    """Encode RGB frames to an H.264 ``mp4`` at ``path`` using PyAV (real runtime path).

    Imported lazily so the module stays importable where ``av`` is unavailable/stubbed; this
    branch is exercised only in the real service, not in unit tests (which inject a fake
    encoder). Kept intentionally small — clip length is driven by the slice the caller passes.
    """
    import av  # lazy — keeps module importable without av; tests inject their own encoder
    import numpy as np

    container = av.open(path, mode="w")
    try:
        stream = container.add_stream("h264", rate=12)
        first = np.asarray(frames[0])
        height, width = int(first.shape[0]), int(first.shape[1])
        stream.width = width
        stream.height = height
        stream.pix_fmt = "yuv420p"
        for frame in frames:
            arr = np.asarray(frame)
            video_frame = av.VideoFrame.from_ndarray(arr, format="rgb24")
            for packet in stream.encode(video_frame):
                container.mux(packet)
        for packet in stream.encode():  # flush
            container.mux(packet)
    finally:
        container.close()


# ---------------------------------------------------------------------------
# Retention / cleanup
# ---------------------------------------------------------------------------

def cleanup_evidence_dir(
    out_dir: str,
    *,
    max_age_days: float = _DEFAULT_MAX_AGE_DAYS,
    max_dir_mb: float = _DEFAULT_MAX_DIR_MB,
    clock: Callable[[], float] = time.time,
    listdir: Optional[Callable[[Path], List[Path]]] = None,
) -> None:
    """Delete stale or excess evidence clips from ``out_dir`` (best-effort).

    Both eviction policies run unconditionally (no short-circuit); a file deleted
    by the time-based pass is simply skipped by the size-based pass.

    Args:
        out_dir: the evidence directory to clean (must exist; silently returns if not).
        max_age_days: delete files whose mtime is older than this many days.
            Pass ``0`` or negative to disable time-based eviction.
        max_dir_mb: delete oldest files (LRU) until the directory is under this limit in MB.
            Pass ``0`` or negative to disable size-based eviction.
        clock: injectable ``() -> float`` returning current Unix timestamp (for testing).
        listdir: injectable ``(path) -> [Path, ...]`` listing ``*.mp4`` files in the dir.
            Defaults to ``sorted(path.glob("evidence_*.mp4"))``.

    Note:
        This function is **best-effort**: all exceptions are caught and logged.
        It never raises, so callers are never disrupted by cleanup errors.
    """
    try:
        _do_cleanup(out_dir, max_age_days=max_age_days, max_dir_mb=max_dir_mb,
                    clock=clock, listdir=listdir)
    except Exception as exc:  # noqa: BLE001
        _log.warning("evidence cleanup failed (best-effort, ignoring): %s: %s",
                     type(exc).__name__, exc)


def _do_cleanup(
    out_dir: str,
    *,
    max_age_days: float,
    max_dir_mb: float,
    clock: Callable[[], float],
    listdir: Optional[Callable[[Path], List[Path]]],
) -> None:
    """Internal: performs the actual cleanup; may raise (callers catch it)."""
    dir_path = Path(out_dir)
    if not dir_path.is_dir():
        return

    _list = listdir if listdir is not None else _default_listdir

    # --- Time-based eviction ------------------------------------------------
    if max_age_days > 0:
        cutoff = clock() - max_age_days * 86400.0
        for p in _list(dir_path):
            try:
                if p.stat().st_mtime < cutoff:
                    p.unlink(missing_ok=True)
                    _log.debug("evidence retention: removed stale file %s", p.name)
            except Exception as exc:  # noqa: BLE001
                _log.warning("evidence retention: could not remove %s: %s", p, exc)

    # --- Size-based eviction (LRU / oldest-first) ----------------------------
    if max_dir_mb > 0:
        max_bytes = max_dir_mb * 1024 * 1024
        # Re-list after the time-based pass (some files may already be gone).
        files = _list(dir_path)
        # Build (mtime, size, path) triples; skip missing files.
        stats: List[Tuple[float, int, Path]] = []
        for p in files:
            try:
                st = p.stat()
                stats.append((st.st_mtime, st.st_size, p))
            except FileNotFoundError:
                pass
        total_bytes = sum(s for _, s, _ in stats)
        if total_bytes > max_bytes:
            # Sort ascending by mtime → oldest first.
            stats.sort(key=lambda t: t[0])
            for mtime, size, p in stats:
                if total_bytes <= max_bytes:
                    break
                try:
                    p.unlink(missing_ok=True)
                    total_bytes -= size
                    _log.debug("evidence retention: removed excess file %s (dir now %.1f MB)",
                               p.name, total_bytes / 1024 / 1024)
                except Exception as exc:  # noqa: BLE001
                    _log.warning("evidence retention: could not remove %s: %s", p, exc)


def _default_listdir(dir_path: Path) -> List[Path]:
    """Return ``evidence_*.mp4`` files sorted by name (stable order for tests)."""
    return sorted(dir_path.glob("evidence_*.mp4"))


# ---------------------------------------------------------------------------
# Main public API
# ---------------------------------------------------------------------------

def save_and_hash(
    frames: Sequence[Any],
    out_dir: str,
    *,
    encoder: Callable[[Sequence[Any], str], None] = _default_encoder,
    filename: str | None = None,
    max_age_days: float = _DEFAULT_MAX_AGE_DAYS,
    max_dir_mb: float = _DEFAULT_MAX_DIR_MB,
    clock: Callable[[], float] = time.time,
    _listdir: Optional[Callable[[Path], List[Path]]] = None,
) -> Tuple[str, str]:
    """Encode ``frames`` to a local ``mp4`` under ``out_dir``; return ``(file:// uri, sha256)``.

    After a successful capture, runs ``cleanup_evidence_dir`` (best-effort) to prevent
    unbounded disk growth.  The cleanup parameters are injectable for testing:

    Args:
        frames: ordered RGB frames (e.g. the alarm window slice of the frame buffer). Must be
            non-empty.
        out_dir: directory to write the clip into (created if missing).
        encoder: ``(frames, path) -> None`` that writes the clip bytes. Injectable for tests.
        filename: optional fixed filename; defaults to a unique ``evidence_<ts>_<uuid>.mp4``.
        max_age_days: passed to ``cleanup_evidence_dir``.
        max_dir_mb: passed to ``cleanup_evidence_dir``.
        clock: injectable clock for cleanup; defaults to ``time.time``.
        _listdir: injectable file-listing callable for cleanup (private, for tests).

    Returns:
        ``(uri, sha256_hexdigest)`` — ``uri`` is ``file://`` + the absolute path; the hash is
        over the bytes actually written to disk.

    Raises:
        EvidenceCaptureError: on empty input, encoder failure, or read-back/hash failure. The
            caller downgrades (submits the event without evidence) rather than dropping it.
        Cleanup errors are swallowed (best-effort) and never surface here.
    """
    frame_list: List[Any] = list(frames)
    if not frame_list:
        raise EvidenceCaptureError("no frames to capture")

    try:
        out_path = Path(out_dir)
        out_path.mkdir(parents=True, exist_ok=True)
        name = filename or f"evidence_{int(time.time())}_{uuid.uuid4().hex}.mp4"
        clip_path = out_path / name

        encoder(frame_list, str(clip_path))

        digest = hashlib.sha256()
        with open(clip_path, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                digest.update(chunk)

        # file:// + absolute path; backend treats this opaquely (upgradeable to s3:// later).
        uri = "file://" + os.path.abspath(str(clip_path)).replace(os.sep, "/")
        if not uri.startswith("file:///"):
            # ensure a leading slash for the path component (Windows drive letters, etc.)
            uri = "file:///" + os.path.abspath(str(clip_path)).replace(os.sep, "/").lstrip("/")
    except EvidenceCaptureError:
        raise
    except Exception as exc:  # noqa: BLE001 — wrap any encoder/IO failure for clean downgrade
        raise EvidenceCaptureError(f"evidence capture failed: {type(exc).__name__}: {exc}") from exc

    # Best-effort cleanup — after successful capture; never raises.
    cleanup_evidence_dir(
        out_dir,
        max_age_days=max_age_days,
        max_dir_mb=max_dir_mb,
        clock=clock,
        listdir=_listdir,
    )

    return uri, digest.hexdigest()
