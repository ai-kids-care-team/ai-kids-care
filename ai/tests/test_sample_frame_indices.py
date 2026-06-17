#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Tests for sample_frame_indices imported from ai_app.inference.pipeline.
Covers 5 boundary scenarios required by the frame-sampling dedup task.
"""
import sys
from pathlib import Path

_SRC_DIR = Path(__file__).resolve().parent.parent / "src"
if str(_SRC_DIR) not in sys.path:
    sys.path.insert(0, str(_SRC_DIR))

import pytest
from ai_app.inference.pipeline import sample_frame_indices


def test_zero_frames_raises():
    """Case 1: total_frames=0 must raise ValueError."""
    with pytest.raises(ValueError):
        sample_frame_indices(total_frames=0)


def test_short_video_uniform_sampling():
    """Case 2: total_frames < clip_len — uniform sampling, length == num_frames."""
    # clip_len = 16 * 4 = 64; total_frames=10 < 64
    result = sample_frame_indices(total_frames=10, num_frames=16, sampling_rate=4)
    assert len(result) == 16
    assert all(0 <= i <= 9 for i in result)


def test_exact_clip_len_center_sampling():
    """Case 3: total_frames == clip_len — center sampling, length == num_frames."""
    # clip_len = 16 * 4 = 64; total_frames=64 == clip_len
    result = sample_frame_indices(total_frames=64, num_frames=16, sampling_rate=4)
    assert len(result) == 16
    assert all(0 <= i <= 63 for i in result)


def test_long_video_center_sampling():
    """Case 4: total_frames >> clip_len — center sampling, length == num_frames."""
    result = sample_frame_indices(total_frames=1000, num_frames=16, sampling_rate=4)
    assert len(result) == 16
    assert all(0 <= i <= 999 for i in result)


@pytest.mark.parametrize("total_frames,num_frames,sampling_rate", [
    (1, 16, 4),
    (16, 16, 4),
    (63, 16, 4),
    (64, 16, 4),
    (100, 16, 4),
    (500, 8, 2),
])
def test_indices_in_bounds(total_frames, num_frames, sampling_rate):
    """Case 5: all returned elements must be in [0, total_frames-1]."""
    result = sample_frame_indices(total_frames, num_frames, sampling_rate)
    assert len(result) == num_frames
    assert all(0 <= i <= total_frames - 1 for i in result)
