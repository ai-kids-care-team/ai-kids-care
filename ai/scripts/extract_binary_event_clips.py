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
import json
import random
import shutil
import subprocess
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
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


def interval_overlap_sec(
        clip_start: float,
        clip_duration: float,
        event_start: float,
        event_duration: float,
) -> float:
    clip_end = clip_start + clip_duration
    event_end = event_start + event_duration
    return max(0.0, min(clip_end, event_end) - max(clip_start, event_start))


def build_positive_clip_starts(
        event_start_sec: float,
        event_duration_sec: float,
        total_duration_sec: float,
        clip_duration: float,
        positive_clips_per_event: int,
        positive_jitter_sec: float,
        min_positive_overlap_ratio: float,
        min_positive_overlap_sec: float,
        max_trials: int = 64,
) -> list[float]:
    event_center_sec = event_start_sec + event_duration_sec / 2.0
    max_start = max(0.0, total_duration_sec - clip_duration)
    starts: list[float] = []

    def is_valid(start_sec: float) -> bool:
        overlap_sec = interval_overlap_sec(
            clip_start=start_sec,
            clip_duration=clip_duration,
            event_start=event_start_sec,
            event_duration=event_duration_sec,
        )
        overlap_ratio = overlap_sec / clip_duration if clip_duration > 0 else 0.0
        return overlap_sec >= min_positive_overlap_sec and overlap_ratio >= min_positive_overlap_ratio

    center_start = min(max(0.0, event_center_sec - clip_duration / 2.0), max_start)
    if is_valid(center_start):
        starts.append(center_start)

    trials = 0
    while len(starts) < positive_clips_per_event and trials < max_trials:
        trials += 1
        jitter = random.uniform(-positive_jitter_sec, positive_jitter_sec)
        candidate = center_start + jitter
        candidate = min(max(0.0, candidate), max_start)
        if not is_valid(candidate):
            continue
        key = round(candidate, 2)
        if any(round(existing, 2) == key for existing in starts):
            continue
        starts.append(candidate)

    if len(starts) < positive_clips_per_event and starts:
        starts.extend([starts[-1]] * (positive_clips_per_event - len(starts)))

    return starts[:positive_clips_per_event]


def summarize_quality(per_clip_df: pd.DataFrame) -> dict:
    summary: dict = {
        "total_clips": int(len(per_clip_df)),
        "positive_clips": int((per_clip_df["clip_role"] == "positive").sum()),
        "negative_clips": int((per_clip_df["clip_role"] == "negative").sum()),
    }
    positive_df = per_clip_df[per_clip_df["clip_role"] == "positive"]
    negative_df = per_clip_df[per_clip_df["clip_role"] == "negative"]

    if len(positive_df) > 0:
        summary["positive_overlap_ratio_min"] = float(positive_df["overlap_ratio"].min())
        summary["positive_overlap_ratio_mean"] = float(positive_df["overlap_ratio"].mean())
        summary["positive_overlap_sec_min"] = float(positive_df["overlap_sec"].min())
        summary["positive_overlap_sec_mean"] = float(positive_df["overlap_sec"].mean())
    else:
        summary["positive_overlap_ratio_min"] = None
        summary["positive_overlap_ratio_mean"] = None
        summary["positive_overlap_sec_min"] = None
        summary["positive_overlap_sec_mean"] = None

    if len(negative_df) > 0:
        summary["negative_margin_violation_count"] = int(negative_df["is_margin_safe"].eq(0).sum())
    else:
        summary["negative_margin_violation_count"] = 0

    summary["split_counts"] = per_clip_df.groupby("split").size().to_dict()
    summary["label_counts"] = per_clip_df.groupby("label").size().to_dict()
    return summary


def update_merged_quality_reports(
        quality_df: pd.DataFrame,
        summary_payload: dict,
        output_manifest: Path,
) -> None:
    merged_quality_path = output_manifest.with_name(output_manifest.stem + "_quality_all_runs.csv")
    merged_summary_path = output_manifest.with_name(output_manifest.stem + "_quality_runs_summary.csv")

    if merged_quality_path.exists():
        existing_quality_df = pd.read_csv(merged_quality_path)
        merged_quality_df = pd.concat([existing_quality_df, quality_df], ignore_index=True)
    else:
        merged_quality_df = quality_df.copy()

    merged_quality_df = merged_quality_df.drop_duplicates(
        subset=["run_id", "video_path", "clip_role", "clip_start_sec"],
        keep="last",
    )
    merged_quality_df.to_csv(merged_quality_path, index=False, encoding="utf-8")

    summary_row = {
        "run_id": summary_payload["run_id"],
        "generated_at": summary_payload["generated_at"],
        "source_manifest": summary_payload["source_manifest"],
        "output_manifest": summary_payload["output_manifest"],
        "total_events": summary_payload["total_events"],
        "skipped_short_events": summary_payload["skipped_short_events"],
        "failed_items": summary_payload["failed_items"],
        "total_clips": summary_payload["quality_summary"]["total_clips"],
        "positive_clips": summary_payload["quality_summary"]["positive_clips"],
        "negative_clips": summary_payload["quality_summary"]["negative_clips"],
        "positive_overlap_ratio_min": summary_payload["quality_summary"]["positive_overlap_ratio_min"],
        "positive_overlap_ratio_mean": summary_payload["quality_summary"]["positive_overlap_ratio_mean"],
        "negative_margin_violation_count": summary_payload["quality_summary"]["negative_margin_violation_count"],
    }
    summary_df = pd.DataFrame([summary_row])

    if merged_summary_path.exists():
        existing_summary_df = pd.read_csv(merged_summary_path)
        merged_summary_df = pd.concat([existing_summary_df, summary_df], ignore_index=True)
        merged_summary_df = merged_summary_df.drop_duplicates(subset=["run_id"], keep="last")
    else:
        merged_summary_df = summary_df

    merged_summary_df.to_csv(merged_summary_path, index=False, encoding="utf-8")
    print(f"Updated merged quality clips: {merged_quality_path}")
    print(f"Updated merged quality summary: {merged_summary_path}")


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
        margin_sec: float = 1.0,
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
            margin_sec=margin_sec,
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
        max_negative_per_positive: int = 1,
        positive_clips_per_event: int = 3,
        positive_jitter_sec: float = 1.0,
        min_positive_overlap_ratio: float = 0.7,
        min_positive_overlap_sec: float = 2.0,
        min_event_duration_sec: float = 5.0,
        negative_margin_sec: float = 2.0,
        use_cross_video_negative: bool = False,
        enable_quality_reports: bool = True,
        merge_quality_reports: bool = True,
        run_id: str | None = None,
        extract_workers: int = 1,
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
    quality_rows = []
    skipped_short_events = 0
    if run_id is None:
        run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    extract_workers = max(1, int(extract_workers))

    def process_manifest_row(row: pd.Series) -> tuple[list[dict], list[dict], list[dict], int]:
        local_rows: list[dict] = []
        local_failed_rows: list[dict] = []
        local_quality_rows: list[dict] = []
        local_skipped_short_events = 0

        source_video = Path(str(row.get("video_path", "")))
        split = str(row.get("split", ""))
        src_label = str(row.get("label", ""))

        try:
            event_start_sec = float(row["event_start_sec"])
            event_duration_sec = float(row["event_duration_sec"])
            total_frames = float(row["total_frames"])
            fps = float(row["fps"])
        except Exception as e:
            local_failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": src_label,
                "error": f"Bad row fields: {e}",
            })
            return local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events

        if fps <= 0:
            local_failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": src_label,
                "error": "Invalid fps",
            })
            return local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events

        if event_duration_sec < min_event_duration_sec:
            local_skipped_short_events += 1
            return local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events

        total_duration_sec = total_frames / fps
        pos_clip_starts_sec = build_positive_clip_starts(
            event_start_sec=event_start_sec,
            event_duration_sec=event_duration_sec,
            total_duration_sec=total_duration_sec,
            clip_duration=clip_duration,
            positive_clips_per_event=positive_clips_per_event,
            positive_jitter_sec=positive_jitter_sec,
            min_positive_overlap_ratio=min_positive_overlap_ratio,
            min_positive_overlap_sec=min_positive_overlap_sec,
        )
        if not pos_clip_starts_sec:
            local_failed_rows.append({
                "video_path": str(source_video),
                "split": split,
                "label": src_label,
                "error": "No valid positive clip start satisfies overlap constraints",
            })
            return local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events

        pos_dir = output_dir / split / src_label
        pos_dir.mkdir(parents=True, exist_ok=True)

        neg_dir = output_dir / split / negative_label
        neg_dir.mkdir(parents=True, exist_ok=True)

        for pos_idx, pos_clip_start_sec in enumerate(pos_clip_starts_sec):
            pos_output = pos_dir / f"{source_video.stem}_pos{pos_idx}.mp4"

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

                local_rows.append({
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

                if enable_quality_reports:
                    overlap_sec = interval_overlap_sec(
                        clip_start=pos_clip_start_sec,
                        clip_duration=clip_duration,
                        event_start=event_start_sec,
                        event_duration=event_duration_sec,
                    )
                    local_quality_rows.append({
                        "run_id": run_id,
                        "split": split,
                        "label": src_label,
                        "clip_role": "positive",
                        "video_path": str(pos_output),
                        "source_video": str(source_video),
                        "clip_start_sec": pos_clip_start_sec,
                        "clip_duration": clip_duration,
                        "event_start_sec": event_start_sec,
                        "event_duration_sec": event_duration_sec,
                        "overlap_sec": overlap_sec,
                        "overlap_ratio": overlap_sec / clip_duration if clip_duration > 0 else 0.0,
                        "negative_margin_sec": negative_margin_sec,
                        "is_margin_safe": 1,
                    })
            except Exception as e:
                local_failed_rows.append({
                    "video_path": str(source_video),
                    "split": split,
                    "label": src_label,
                    "error": f"positive clip failed: {e}",
                })
                continue

            negative_jobs: list[tuple[str, str, float]] = []

            intra_candidates = collect_intra_video_negative_starts(
                event_start_sec=event_start_sec,
                event_duration_sec=event_duration_sec,
                total_duration_sec=total_duration_sec,
                clip_duration=clip_duration,
                margin_sec=negative_margin_sec,
                num_random_trials=20,
            )

            if intra_candidates:
                source_type, start_sec = random.choice(intra_candidates)
                negative_jobs.append((source_type, str(source_video), start_sec))

            if use_cross_video_negative and len(negative_jobs) < max_negative_per_positive:
                cross_sample = sample_cross_video_negative(
                    df=df,
                    current_video_path=str(source_video),
                    clip_duration=clip_duration,
                    split=split,
                    margin_sec=negative_margin_sec,
                    max_trials=30,
                )
                if cross_sample is not None:
                    other_video_path, start_sec = cross_sample
                    negative_jobs.append(("cross_video", other_video_path, start_sec))

            negative_jobs = negative_jobs[:max_negative_per_positive]

            for neg_idx, (source_type, neg_source_video, neg_clip_start_sec) in enumerate(negative_jobs):
                neg_source_video = Path(neg_source_video)
                neg_output = neg_dir / f"{source_video.stem}_pos{pos_idx}_neg{neg_idx}_{source_type}.mp4"

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

                    local_rows.append({
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

                    if enable_quality_reports:
                        is_margin_safe = int(
                            not overlaps_event(
                                clip_start=neg_clip_start_sec,
                                clip_duration=clip_duration,
                                event_start=event_start_sec,
                                event_duration=event_duration_sec,
                                margin_sec=negative_margin_sec,
                            )
                        )
                        overlap_sec = interval_overlap_sec(
                            clip_start=neg_clip_start_sec,
                            clip_duration=clip_duration,
                            event_start=event_start_sec,
                            event_duration=event_duration_sec,
                        )
                        local_quality_rows.append({
                            "run_id": run_id,
                            "split": split,
                            "label": negative_label,
                            "clip_role": "negative",
                            "video_path": str(neg_output),
                            "source_video": str(neg_source_video),
                            "clip_start_sec": neg_clip_start_sec,
                            "clip_duration": clip_duration,
                            "event_start_sec": event_start_sec,
                            "event_duration_sec": event_duration_sec,
                            "overlap_sec": overlap_sec,
                            "overlap_ratio": overlap_sec / clip_duration if clip_duration > 0 else 0.0,
                            "negative_margin_sec": negative_margin_sec,
                            "is_margin_safe": is_margin_safe,
                        })
                except Exception as e:
                    local_failed_rows.append({
                        "video_path": str(neg_source_video),
                        "split": split,
                        "label": negative_label,
                        "error": f"negative clip failed: {e}",
                    })

        return local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events

    def process_indexed_row(item: tuple[int, pd.Series]) -> tuple[list[dict], list[dict], list[dict], int]:
        _, one_row = item
        return process_manifest_row(one_row)

    indexed_rows = list(df.iterrows())

    if extract_workers == 1:
        iterator = tqdm(indexed_rows, total=len(indexed_rows), desc="Extracting binary clips")
        for item in iterator:
            local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events = process_indexed_row(item)
            rows.extend(local_rows)
            failed_rows.extend(local_failed_rows)
            quality_rows.extend(local_quality_rows)
            skipped_short_events += local_skipped_short_events
    else:
        print(f"Using extract_workers={extract_workers}")
        with ThreadPoolExecutor(max_workers=extract_workers) as executor:
            iterator = executor.map(process_indexed_row, indexed_rows)
            for local_rows, local_failed_rows, local_quality_rows, local_skipped_short_events in tqdm(
                    iterator,
                    total=len(indexed_rows),
                    desc=f"Extracting binary clips ({extract_workers} workers)",
            ):
                rows.extend(local_rows)
                failed_rows.extend(local_failed_rows)
                quality_rows.extend(local_quality_rows)
                skipped_short_events += local_skipped_short_events

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
    if skipped_short_events > 0:
        print(f"Skipped short events (< {min_event_duration_sec:.1f}s): {skipped_short_events}")

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

    if enable_quality_reports and quality_rows:
        quality_df = pd.DataFrame(quality_rows)
        quality_csv_path = output_manifest.with_name(output_manifest.stem + f"_quality_{run_id}.csv")
        quality_summary_path = output_manifest.with_name(output_manifest.stem + f"_quality_summary_{run_id}.json")

        quality_df.to_csv(quality_csv_path, index=False, encoding="utf-8")
        quality_summary = summarize_quality(quality_df)
        summary_payload = {
            "run_id": run_id,
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "source_manifest": str(manifest_path),
            "output_manifest": str(output_manifest),
            "total_events": int(len(df)),
            "skipped_short_events": int(skipped_short_events),
            "failed_items": int(len(failed_rows)),
            "quality_summary": quality_summary,
        }
        with open(quality_summary_path, "w", encoding="utf-8") as f:
            json.dump(summary_payload, f, ensure_ascii=False, indent=2)

        print(f"Saved quality clips: {quality_csv_path}")
        print(f"Saved quality summary: {quality_summary_path}")

        if merge_quality_reports:
            update_merged_quality_reports(
                quality_df=quality_df,
                summary_payload=summary_payload,
                output_manifest=output_manifest,
            )


if __name__ == "__main__":
    dataset_tag = "06_wander"
    data_root = Path("../data/processed")
    extract_binary_clips(
        manifest_path=data_root / f"{dataset_tag}_manifest.csv",
        output_dir=data_root / f"{dataset_tag}_event_clips",
        output_manifest=data_root / f"{dataset_tag}_manifest_clips.csv",
        negative_label="normal",
        clip_duration=5.0,
        use_gpu=True,
        use_hwaccel=False,
        overwrite=False,
        max_negative_per_positive=1,
        positive_clips_per_event=3,
        positive_jitter_sec=1.0,
        min_positive_overlap_ratio=0.7,
        min_positive_overlap_sec=2.0,
        min_event_duration_sec=5.0,
        negative_margin_sec=2.0,
        use_cross_video_negative=False,
        enable_quality_reports=True,
        merge_quality_reports=True,
        run_id=None,
        extract_workers=4,
        random_seed=42,
    )
