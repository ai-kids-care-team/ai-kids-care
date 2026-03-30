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
import random
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


def safe_total_duration_sec(total_frames: float, fps: float) -> float | None:
    if fps <= 0:
        return None
    return total_frames / fps


def overlaps_event(
        clip_start: float,
        clip_duration: float,
        event_start: float,
        event_duration: float,
        margin_sec: float = 1.0,
) -> bool:
    clip_end = clip_start + clip_duration
    event_end = event_start + event_duration

    safe_event_start = max(0.0, event_start - margin_sec)
    safe_event_end = event_end + margin_sec

    return not (clip_end <= safe_event_start or clip_start >= safe_event_end)


def collect_intra_video_negative_starts(
        event_start_sec: float,
        event_duration_sec: float,
        total_duration_sec: float,
        clip_duration: float,
        margin_sec: float = 1.0,
        num_random_trials: int = 20,
) -> list[tuple[str, float]]:
    """
    Return candidate negative clip starts from the SAME source video.

    Returns list of tuples:
    [
        ("before", start_sec),
        ("after", start_sec),
        ("random_intra", start_sec),
        ...
    ]
    """
    candidates: list[tuple[str, float]] = []

    event_end_sec = event_start_sec + event_duration_sec

    # 1) before-event clip
    before_start = event_start_sec - margin_sec - clip_duration
    if before_start >= 0:
        candidates.append(("before", before_start))

    # 2) after-event clip
    after_start = event_end_sec + margin_sec
    if after_start + clip_duration <= total_duration_sec:
        candidates.append(("after", after_start))

    # 3) random intra-video negative
    if total_duration_sec > clip_duration:
        for _ in range(num_random_trials):
            start = random.uniform(0, total_duration_sec - clip_duration)
            if not overlaps_event(
                    clip_start=start,
                    clip_duration=clip_duration,
                    event_start=event_start_sec,
                    event_duration=event_duration_sec,
                    margin_sec=margin_sec,
            ):
                candidates.append(("random_intra", start))

    # deduplicate near-identical starts
    dedup = []
    seen = set()
    for role, start in candidates:
        key = round(start, 1)
        if key not in seen:
            seen.add(key)
            dedup.append((role, start))

    return dedup


def sample_cross_video_negative(
        df: pd.DataFrame,
        current_video_path: str,
        clip_duration: float,
        split: str,
        max_trials: int = 30,
) -> tuple[str, float] | None:
    """
    Sample one negative clip from a DIFFERENT source video.

    Returns:
        (other_video_path, start_sec)
    """
    same_split_df = df[df["split"] == split]
    candidate_df = same_split_df[same_split_df["video_path"] != current_video_path]

    if len(candidate_df) == 0:
        return None

    for _ in range(max_trials):
        row = candidate_df.sample(n=1).iloc[0]

        other_video = str(row["video_path"])
        total_frames = float(row["total_frames"])
        fps = float(row["fps"])
        event_start_sec = float(row["event_start_sec"])
        event_duration_sec = float(row["event_duration_sec"])

        total_duration_sec = safe_total_duration_sec(total_frames, fps)
        if total_duration_sec is None or total_duration_sec <= clip_duration:
            continue

        candidates = collect_intra_video_negative_starts(
            event_start_sec=event_start_sec,
            event_duration_sec=event_duration_sec,
            total_duration_sec=total_duration_sec,
            clip_duration=clip_duration,
            margin_sec=1.0,
            num_random_trials=10,
        )

        if not candidates:
            continue

        _, start_sec = random.choice(candidates)
        return other_video, start_sec

    return None


def extract_binary_clips(
        manifest_path: str | Path,
        output_dir: str | Path,
        output_manifest: str | Path,
        negative_label: str = "normal",
        clip_duration: float = 5.0,
        use_gpu: bool = True,
        use_hwaccel: bool = False,
        overwrite: bool = False,
        max_negative_per_positive: int = 2,
        random_seed: int = 42,
) -> None:
    check_ffmpeg_available()
    random.seed(random_seed)

    manifest_path = Path(manifest_path).resolve()
    output_dir = Path(output_dir).resolve()
    output_manifest = Path(output_manifest).resolve()

    output_dir.mkdir(parents=True, exist_ok=True)
    output_manifest.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(manifest_path)
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
        pos_dir = output_dir / split / src_label
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
                "label": src_label,
                "split": split,
                "source_video": str(source_video),
                "clip_start_sec": pos_clip_start_sec,
                "clip_duration": clip_duration,
                "event_start_sec": event_start_sec,
                "event_duration_sec": event_duration_sec,
                "clip_role": "positive",
                "negative_source_type": "",
            })
        except Exception as e:
            failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": src_label,
                "error": f"positive clip failed: {e}",
            })
            continue

        # ---------- diversified negatives ----------
        negative_jobs: list[tuple[str, str, float]] = []
        # format: (source_type, input_video_path, start_sec)

        intra_candidates = collect_intra_video_negative_starts(
            event_start_sec=event_start_sec,
            event_duration_sec=event_duration_sec,
            total_duration_sec=total_duration_sec,
            clip_duration=clip_duration,
            margin_sec=1.0,
            num_random_trials=20,
        )

        # 优先从同视频选一个
        if intra_candidates:
            source_type, start_sec = random.choice(intra_candidates)
            negative_jobs.append((source_type, str(source_video), start_sec))

        # 再尝试从其他视频选一个
        cross_sample = sample_cross_video_negative(
            df=df,
            current_video_path=str(source_video),
            clip_duration=clip_duration,
            split=split,
            max_trials=30,
        )
        if cross_sample is not None:
            other_video_path, start_sec = cross_sample
            negative_jobs.append(("cross_video", other_video_path, start_sec))

        # 限制数量
        negative_jobs = negative_jobs[:max_negative_per_positive]

        neg_dir = output_dir / split / negative_label
        neg_dir.mkdir(parents=True, exist_ok=True)

        for neg_idx, (source_type, neg_source_video, neg_clip_start_sec) in enumerate(negative_jobs):
            neg_source_video = Path(neg_source_video)
            neg_output = neg_dir / f"{source_video.stem}_neg{neg_idx}_{source_type}.mp4"

            try:
                if not neg_output.exists() or overwrite:
                    run_ffmpeg(
                        input_path=neg_source_video,
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
                    "source_video": str(neg_source_video),
                    "clip_start_sec": neg_clip_start_sec,
                    "clip_duration": clip_duration,
                    "event_start_sec": event_start_sec,
                    "event_duration_sec": event_duration_sec,
                    "clip_role": "negative",
                    "negative_source_type": source_type,
                })
            except Exception as e:
                failed_rows.append({
                    "video_path": str(neg_source_video),
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
        "negative_source_type",
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
        manifest_path="../data/processed/01_manifest.csv",
        output_dir="../data/processed/event_clips",
        output_manifest="../data/processed/01_manifest_clips.csv",
        negative_label="normal",
        clip_duration=5.0,
        use_gpu=True,
        use_hwaccel=False,
        overwrite=False,
        max_negative_per_positive=2,
        random_seed=42,
    )
