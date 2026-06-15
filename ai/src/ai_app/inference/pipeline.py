#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 20:15
@Author  : zhangjunfan1997@naver.com
@File    : pipeline
"""
from __future__ import annotations

from pathlib import Path

import av
import numpy as np


def decode_video_pyav(video_path: str | Path) -> list[np.ndarray]:
    """
    Decode all frames from a short clip using PyAV.
    Returns RGB frames as numpy arrays.
    """
    container = av.open(str(video_path))
    frames = []
    try:
        for frame in container.decode(video=0):
            frames.append(frame.to_ndarray(format="rgb24"))
    finally:
        container.close()
    return frames


def sample_frame_indices(
        total_frames: int,
        num_frames: int = 16,
        sampling_rate: int = 4,
) -> list[int]:
    """
    Sampling strategy aligned with the current training/eval flow:
    - if enough frames: center sampling with stride
    - otherwise: uniform sampling over the whole clip
    """
    clip_len = num_frames * sampling_rate

    if total_frames <= 0:
        raise ValueError("Video contains no decodable frames.")

    if total_frames >= clip_len:
        start_idx = (total_frames - clip_len) // 2
        indices = start_idx + np.arange(num_frames) * sampling_rate
        indices = np.clip(indices, 0, total_frames - 1)
        return indices.astype(int).tolist()

    indices = np.linspace(0, total_frames - 1, num=num_frames)
    indices = np.clip(np.round(indices).astype(int), 0, total_frames - 1)
    return indices.tolist()
