#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-30 15:29
@Author  : zhangjunfan1997@naver.com
@File    : extract_binary_event_clips
"""
# !/usr/bin/env python
# -*- coding: utf-8 -*-

from __future__ import annotations

import csv
import shutil
import subprocess
from pathlib import Path

import pandas as pd
from tqdm import tqdm


def check_ffmpeg_available() -> None:
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
    input_path = Path(input_path)
    output_path = Path(output_path)

    cmd = ["ffmpeg"]

    cmd.append("-y" if overwrite else "-n")

    if use_hwaccel:
        cmd.extend(["-hwaccel", "cuda"])

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


def choose_negative_clip_start(
        event_start_sec: float,
        event_duration_sec: float,
        total_duration_sec: float,
        clip_duration: float,
        margin_sec: float = 1.0,
) -> float | None:
    """
    Choose a negative clip that does NOT overlap with the event region.

    Priority:
    1. clip before event
    2. clip after event
    3. if neither fits, return None
    """
    event_end_sec = event_start_sec + event_duration_sec

    # valid negative regions:
    # [0, event_start_sec - margin_sec)
    # (event_end_sec + margin_sec, total_duration_sec]
    before_end = event_start_sec - margin_sec
    after_start = event_end_sec + margin_sec

    # Try before-event region first
    if before_end >= clip_duration:
        return max(0.0, before_end - clip_duration)

    # Then try after-event region
    if total_duration_sec - after_start >= clip_duration:
        return after_start

    return None


def extract_binary_clips(
        manifest_path: str | Path,
        output_dir: str | Path,
        output_manifest: str | Path,
        positive_label: str = "assault",
        negative_label: str = "normal",
        clip_duration: float = 5.0,
        use_gpu: bool = True,
        use_hwaccel: bool = False,
        overwrite: bool = False,
) -> None:
    check_ffmpeg_available()

    manifest_path = Path(manifest_path).resolve()
    output_dir = Path(output_dir).resolve()
    output_manifest = Path(output_manifest).resolve()

    output_dir.mkdir(parents=True, exist_ok=True)
    output_manifest.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(manifest_path)[0:100]
    if len(df) == 0:
        raise ValueError(f"Manifest is empty: {manifest_path}")

    required_cols = {
        "video_path",
        "label",
        "split",
        "event_start_sec",
        "event_duration_sec",
        "total_frames",
        "fps",
    }
    missing = required_cols - set(df.columns)
    if missing:
        raise ValueError(f"Missing required columns in manifest: {missing}")

    rows = []
    failed_rows = []

    for _, row in tqdm(df.iterrows(), total=len(df), desc="Extracting binary clips"):
        source_video = Path(row["video_path"])
        split = str(row["split"])
        src_label = str(row["label"])

        event_start_sec = float(row["event_start_sec"])
        event_duration_sec = float(row["event_duration_sec"])
        total_frames = float(row["total_frames"])
        fps = float(row["fps"])

        if fps <= 0:
            failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": src_label,
                "error": "Invalid fps",
            })
            continue

        total_duration_sec = total_frames / fps
        event_center_sec = event_start_sec + event_duration_sec / 2.0
        pos_clip_start_sec = max(0.0, event_center_sec - clip_duration / 2.0)

        # ---------- positive clip ----------
        pos_dir = output_dir / split / positive_label
        pos_dir.mkdir(parents=True, exist_ok=True)

        pos_output = pos_dir / f"{source_video.stem}_pos.mp4"

        try:
            if not pos_output.exists() or overwrite:
                run_ffmpeg(
                    input_path=source_video,
                    output_path=pos_output,
                    start_sec=pos_clip_start_sec,
                    duration_sec=clip_duration,
                    use_gpu=use_gpu,
                    use_hwaccel=use_hwaccel,
                    overwrite=overwrite,
                )

            rows.append({
                "video_path": str(pos_output),
                "label": positive_label,
                "split": split,
                "source_video": str(source_video),
                "clip_start_sec": pos_clip_start_sec,
                "clip_duration": clip_duration,
                "event_start_sec": event_start_sec,
                "event_duration_sec": event_duration_sec,
                "clip_role": "positive",
            })
        except Exception as e:
            failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": positive_label,
                "error": f"positive clip failed: {e}",
            })
            continue

        # ---------- negative clip ----------
        neg_clip_start_sec = choose_negative_clip_start(
            event_start_sec=event_start_sec,
            event_duration_sec=event_duration_sec,
            total_duration_sec=total_duration_sec,
            clip_duration=clip_duration,
            margin_sec=1.0,
        )

        if neg_clip_start_sec is None:
            failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": negative_label,
                "error": "no valid negative window found",
            })
            continue

        neg_dir = output_dir / split / negative_label
        neg_dir.mkdir(parents=True, exist_ok=True)

        neg_output = neg_dir / f"{source_video.stem}_neg.mp4"

        try:
            if not neg_output.exists() or overwrite:
                run_ffmpeg(
                    input_path=source_video,
                    output_path=neg_output,
                    start_sec=neg_clip_start_sec,
                    duration_sec=clip_duration,
                    use_gpu=use_gpu,
                    use_hwaccel=use_hwaccel,
                    overwrite=overwrite,
                )

            rows.append({
                "video_path": str(neg_output),
                "label": negative_label,
                "split": split,
                "source_video": str(source_video),
                "clip_start_sec": neg_clip_start_sec,
                "clip_duration": clip_duration,
                "event_start_sec": event_start_sec,
                "event_duration_sec": event_duration_sec,
                "clip_role": "negative",
            })
        except Exception as e:
            failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": negative_label,
                "error": f"negative clip failed: {e}",
            })

    fieldnames = [
        "video_path",
        "label",
        "split",
        "source_video",
        "clip_start_sec",
        "clip_duration",
        "event_start_sec",
        "event_duration_sec",
        "clip_role",
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
                fieldnames=["video_path", "split", "label", "error"],
            )
            writer.writeheader()
            writer.writerows(failed_rows)
        print(f"Saved {len(failed_rows)} failed records to {failed_manifest}")


if __name__ == "__main__":
    extract_binary_clips(
        manifest_path="../data/processed/manifest.csv",
        output_dir="../data/processed/event_clips_binary",
        output_manifest="../data/processed/manifest_clips_binary.csv",
        positive_label="assault",
        negative_label="normal",
        clip_duration=5.0,
        use_gpu=True,
        use_hwaccel=False,
        overwrite=False,
    )
