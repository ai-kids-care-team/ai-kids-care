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
"""

from __future__ import annotations

import hashlib
import os
import time
import uuid
from pathlib import Path
from typing import Any, Callable, List, Sequence, Tuple


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


def save_and_hash(
    frames: Sequence[Any],
    out_dir: str,
    *,
    encoder: Callable[[Sequence[Any], str], None] = _default_encoder,
    filename: str | None = None,
) -> Tuple[str, str]:
    """Encode ``frames`` to a local ``mp4`` under ``out_dir``; return ``(file:// uri, sha256)``.

    Args:
        frames: ordered RGB frames (e.g. the alarm window slice of the frame buffer). Must be
            non-empty.
        out_dir: directory to write the clip into (created if missing).
        encoder: ``(frames, path) -> None`` that writes the clip bytes. Injectable for tests.
        filename: optional fixed filename; defaults to a unique ``evidence_<ts>_<uuid>.mp4``.

    Returns:
        ``(uri, sha256_hexdigest)`` — ``uri`` is ``file://`` + the absolute path; the hash is
        over the bytes actually written to disk.

    Raises:
        EvidenceCaptureError: on empty input, encoder failure, or read-back/hash failure. The
            caller downgrades (submits the event without evidence) rather than dropping it.
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
        return uri, digest.hexdigest()
    except EvidenceCaptureError:
        raise
    except Exception as exc:  # noqa: BLE001 — wrap any encoder/IO failure for clean downgrade
        raise EvidenceCaptureError(f"evidence capture failed: {type(exc).__name__}: {exc}") from exc
