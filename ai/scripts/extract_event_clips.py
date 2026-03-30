#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-30 12:33
@Author  : zhangjunfan1997@naver.com
@File    : extract_event_clips
"""
from __future__ import annotations

"""
Extract fixed-duration event-centered clips from long videos using FFmpeg.

Features:
- CPU or GPU encoding
- Optional CUDA hwaccel
- Event-centered clipping
- Output manifest generation
"""

import csv
import shutil
import subprocess
from pathlib import Path

import pandas as pd
from tqdm import tqdm


def check_ffmpeg_available() -> None:
    """
    Ensure ffmpeg is installed and available in PATH.
    """
    ffmpeg_path = shutil.which("ffmpeg")
    if ffmpeg_path is None:
        raise EnvironmentError(
            "ffmpeg not found in PATH. Please install FFmpeg and add it to PATH first."
        )


def run_ffmpeg(
        input_path: str | Path,
        output_path: str | Path,
        start_sec: float,
        duration_sec: float,
        use_gpu: bool = True,
        use_hwaccel: bool = False,
        overwrite: bool = True,
) -> None:
    """
    Extract one clip with ffmpeg.

    Parameters
    ----------
    input_path : str | Path
        Source video path.
    output_path : str | Path
        Output clip path.
    start_sec : float
        Clip start time in seconds.
    duration_sec : float
        Clip duration in seconds.
    use_gpu : bool
        Whether to use h264_nvenc.
    use_hwaccel : bool
        Whether to add '-hwaccel cuda' for decoding.
    overwrite : bool
        Whether to overwrite existing file.
    """
    input_path = Path(input_path)
    output_path = Path(output_path)

    cmd = ["ffmpeg"]

    if overwrite:
        cmd.append("-y")
    else:
        cmd.append("-n")

    if use_hwaccel:
        cmd.extend(["-hwaccel", "cuda"])

    # Fast seek: place -ss before -i
    cmd.extend([
        "-ss", f"{start_sec:.3f}",
        "-i", str(input_path),
        "-t", f"{duration_sec:.3f}",
    ])

    if use_gpu:
        cmd.extend([
            "-c:v", "h264_nvenc",
            "-preset", "p4",
            "-cq", "23",
            "-c:a", "aac",
        ])
    else:
        cmd.extend([
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "23",
            "-c:a", "aac",
        ])

    cmd.extend([
        "-movflags", "+faststart",
        "-loglevel", "error",
        str(output_path),
    ])

    subprocess.run(cmd, check=True)


def extract_clips(
        manifest_path: str | Path,
        output_dir: str | Path,
        output_manifest: str | Path,
        clip_duration: float = 5.0,
        use_gpu: bool = True,
        use_hwaccel: bool = False,
        overwrite: bool = False,
) -> None:
    """
    Read manifest.csv, extract event-centered clips, and write manifest_clips.csv.
    """
    check_ffmpeg_available()

    manifest_path = Path(manifest_path).resolve()
    output_dir = Path(output_dir).resolve()
    output_manifest = Path(output_manifest).resolve()

    output_dir.mkdir(parents=True, exist_ok=True)
    output_manifest.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(manifest_path)
    if len(df) == 0:
        raise ValueError(f"Manifest is empty: {manifest_path}")

    required_cols = {"video_path", "label", "split"}
    missing_cols = required_cols - set(df.columns)
    if missing_cols:
        raise ValueError(f"Missing required columns in manifest: {missing_cols}")

    rows = []
    failed_rows = []

    for _, row in tqdm(df.iterrows(), total=len(df), desc="Extracting clips"):
        video_path = Path(row["video_path"])
        label = str(row["label"])
        split = str(row["split"])

        start_sec = row.get("event_start_sec", None)
        duration_sec = row.get("event_duration_sec", None)

        if pd.isna(start_sec):
            start_sec = 0.0
        else:
            start_sec = float(start_sec)

        if pd.isna(duration_sec):
            duration_sec = clip_duration
        else:
            duration_sec = float(duration_sec)

        # Event-centered fixed-length clip
        center_sec = start_sec + duration_sec / 2.0
        clip_start_sec = max(0.0, center_sec - clip_duration / 2.0)

        clip_name = f"{video_path.stem}.mp4"
        clip_dir = output_dir / split / label
        clip_dir.mkdir(parents=True, exist_ok=True)

        output_path = clip_dir / clip_name

        try:
            # Skip if already exists and not overwrite
            if output_path.exists() and not overwrite:
                pass
            else:
                run_ffmpeg(
                    input_path=video_path,
                    output_path=output_path,
                    start_sec=clip_start_sec,
                    duration_sec=clip_duration,
                    use_gpu=use_gpu,
                    use_hwaccel=use_hwaccel,
                    overwrite=overwrite,
                )

            rows.append({
                "video_path": str(output_path),
                "label": label,
                "split": split,
                "source_video": str(video_path),
                "clip_start_sec": clip_start_sec,
                "clip_duration": clip_duration,
                "event_start_sec": start_sec,
                "event_duration_sec": duration_sec,
            })

        except subprocess.CalledProcessError as e:
            failed_rows.append({
                "video_path": str(video_path),
                "label": label,
                "split": split,
                "error": str(e),
            })
            print(f"[ERROR] FFmpeg failed: {video_path}")

        except Exception as e:
            failed_rows.append({
                "video_path": str(video_path),
                "label": label,
                "split": split,
                "error": str(e),
            })
            print(f"[ERROR] Unexpected error: {video_path} -> {e}")

    fieldnames = [
        "video_path",
        "label",
        "split",
        "source_video",
        "clip_start_sec",
        "clip_duration",
        "event_start_sec",
        "event_duration_sec",
    ]

    with open(output_manifest, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"\nSaved {len(rows)} clips to {output_manifest}")

    if failed_rows:
        failed_manifest = output_manifest.with_name(output_manifest.stem + "_failed.csv")
        with open(failed_manifest, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["video_path", "label", "split", "error"]
            )
            writer.writeheader()
            writer.writerows(failed_rows)
        print(f"Saved {len(failed_rows)} failed records to {failed_manifest}")


if __name__ == "__main__":
    extract_clips(
        manifest_path="../data/processed/manifest.csv",
        output_dir="../data/processed/event_clips",
        output_manifest="../data/processed/manifest_clips.csv",
        clip_duration=5.0,
        use_gpu=True,  # True: h264_nvenc / False: libx264
        use_hwaccel=False,  # 先关掉，确认稳定后再试 True
        overwrite=False,
    )
