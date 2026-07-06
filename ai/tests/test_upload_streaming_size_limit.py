"""
Tests for SEC-04: the upload size limit must be enforced by incremental/streamed reads,
never by buffering the entire (potentially huge) payload into memory first.

These tests exercise ``ai_app.serving.app._read_upload_within_limit`` directly with a
fake upload-file stand-in that tracks how many bytes/chunks were actually consumed,
so we can assert the read loop aborts as soon as the cumulative size crosses the limit
rather than draining the whole stream.
"""
from __future__ import annotations

import asyncio

import pytest
from fastapi import HTTPException

from ai_app.serving.app import _read_upload_within_limit


class _FakeUpload:
    """Async file-like stand-in that serves bounded chunks and counts reads."""

    def __init__(self, total_size: int, chunk_size: int = 1024 * 1024) -> None:
        self._remaining = total_size
        self._chunk_size = chunk_size
        self.chunks_served = 0
        self.bytes_served = 0
        self.closed = False

    async def read(self, size: int = -1) -> bytes:
        if self._remaining <= 0:
            return b""
        want = size if size and size > 0 else self._remaining
        n = min(want, self._remaining, self._chunk_size)
        self._remaining -= n
        self.chunks_served += 1
        self.bytes_served += n
        return b"\x00" * n

    async def close(self) -> None:
        self.closed = True


def test_oversized_upload_aborts_before_full_buffering():
    """A huge declared upload must raise 413 without the loop ever reading it all."""
    total_size = 2000 * 1024 * 1024  # 2000 MB — far beyond the 10 MB limit below
    fake = _FakeUpload(total_size=total_size, chunk_size=1024 * 1024)  # 1 MiB chunks

    with pytest.raises(HTTPException) as exc_info:
        asyncio.run(_read_upload_within_limit(fake, max_upload_mb=10))

    assert exc_info.value.status_code == 413
    assert "10 MB limit" in exc_info.value.detail
    # Proof of no full buffering: reading the whole 2000 MB in 1 MiB chunks would take
    # ~2000 reads; the loop must abort long before that, once the running total > 10 MB.
    assert fake.chunks_served <= 12, (
        f"expected the read loop to abort within ~11 chunks of a 10 MB limit, "
        f"got {fake.chunks_served} chunks ({fake.bytes_served} bytes served)"
    )
    assert fake.bytes_served < total_size
    assert fake.closed is True


def test_within_limit_upload_reads_fully_and_returns_content():
    payload_size = 2 * 1024 * 1024  # 2 MB
    fake = _FakeUpload(total_size=payload_size, chunk_size=512 * 1024)

    content = asyncio.run(_read_upload_within_limit(fake, max_upload_mb=10))

    assert len(content) == payload_size
    assert fake.closed is False  # only closed on the rejection path


def test_upload_exactly_at_limit_passes():
    limit_mb = 5
    payload_size = limit_mb * 1024 * 1024
    fake = _FakeUpload(total_size=payload_size, chunk_size=1024 * 1024)

    content = asyncio.run(_read_upload_within_limit(fake, max_upload_mb=limit_mb))

    assert len(content) == payload_size
