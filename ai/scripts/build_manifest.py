#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Build manifest from videos + same-name XML annotations.

Output columns:
- video_path
- xml_path
- label
- split
- event_start_time
- event_duration
- event_start_sec
- event_duration_sec
- fps
- total_frames
- event_start_frame
- event_end_frame
"""

from pathlib import Path
import csv
import random
import xml.etree.ElementTree as ET
from collections import defaultdict, Counter

VIDEO_EXTS = {".mp4", ".avi", ".mov", ".mkv", ".webm"}


def parse_time_to_seconds(time_str: str | None) -> float | None:
    """
    支持以下格式：
    - HH:MM:SS.s
    - MM:SS.s
    - SS.s
    """
    if not time_str:
        return None

    time_str = time_str.strip()
    if not time_str:
        return None

    parts = time_str.split(":")

    try:
        if len(parts) == 3:
            # HH:MM:SS.s
            hours = int(parts[0])
            minutes = int(parts[1])
            seconds = float(parts[2])
            return hours * 3600 + minutes * 60 + seconds

        elif len(parts) == 2:
            # MM:SS.s
            minutes = int(parts[0])
            seconds = float(parts[1])
            return minutes * 60 + seconds

        elif len(parts) == 1:
            # SS.s
            return float(parts[0])

        else:
            raise ValueError(f"Invalid time format: {time_str}")

    except Exception:
        raise ValueError(f"Invalid time format: {time_str}")


def parse_xml_annotation(xml_path: Path) -> dict:
    """
    Parse one XML annotation file.
    """
    tree = ET.parse(xml_path)
    root = tree.getroot()

    label = root.findtext("./event/eventname")
    event_start_time = root.findtext("./event/starttime")
    event_duration = root.findtext("./event/duration")

    fps_text = root.findtext("./header/fps")
    total_frames_text = root.findtext("./header/frames")

    fps = float(fps_text) if fps_text else None
    total_frames = int(total_frames_text) if total_frames_text else None

    event_start_sec = parse_time_to_seconds(event_start_time)
    event_duration_sec = parse_time_to_seconds(event_duration)

    event_start_frame = None
    event_end_frame = None

    if fps is not None and event_start_sec is not None:
        event_start_frame = int(round(event_start_sec * fps))

    if fps is not None and event_start_sec is not None and event_duration_sec is not None:
        event_end_frame = int(round((event_start_sec + event_duration_sec) * fps))

        if total_frames is not None:
            event_start_frame = min(event_start_frame, total_frames - 1)
            event_end_frame = min(event_end_frame, total_frames - 1)

    return {
        "label": label.strip() if label else None,
        "event_start_time": event_start_time,
        "event_duration": event_duration,
        "event_start_sec": event_start_sec,
        "event_duration_sec": event_duration_sec,
        "fps": fps,
        "total_frames": total_frames,
        "event_start_frame": event_start_frame,
        "event_end_frame": event_end_frame,
    }


def infer_scene_group(video_path: Path, root_dir: Path) -> str:
    """
    Infer scene group from relative path:
    root/<event_category>/<scene_group>/...
    """
    try:
        rel_parts = video_path.relative_to(root_dir).parts
    except ValueError:
        return "unknown"

    if len(rel_parts) >= 2:
        return rel_parts[1]
    return "unknown"


def compute_split_counts(
        n: int,
        train_ratio: float,
        val_ratio: float,
        test_ratio: float,
) -> tuple[int, int, int]:
    if n <= 0:
        return 0, 0, 0

    # For very small strata, keep deterministic and simple.
    if n == 1:
        return 1, 0, 0
    if n == 2:
        if test_ratio >= val_ratio:
            return 1, 0, 1
        return 1, 1, 0

    # n >= 3
    n_val = max(1, int(n * val_ratio))
    n_test = max(1, int(n * test_ratio))
    n_train = n - n_val - n_test

    if n_train < 1:
        deficit = 1 - n_train
        while deficit > 0 and n_val > 1:
            n_val -= 1
            deficit -= 1
        while deficit > 0 and n_test > 1:
            n_test -= 1
            deficit -= 1
        n_train = n - n_val - n_test

    if n_train < 1:
        n_train = 1
        if n_val > n_test and n_val > 1:
            n_val -= 1
        elif n_test > 1:
            n_test -= 1

    return n_train, n_val, n_test


def build_manifest_with_split(
        root_dir: str,
        out_csv: str,
        train_ratio: float = 0.8,
        val_ratio: float = 0.1,
        test_ratio: float = 0.1,
        seed: int = 42,
        require_xml: bool = True,
):
    assert abs(train_ratio + val_ratio + test_ratio - 1.0) < 1e-6, \
        "train_ratio + val_ratio + test_ratio must sum to 1.0"

    rng = random.Random(seed)

    root = Path(root_dir).resolve()
    out_path = Path(out_csv).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    samples_by_label = defaultdict(list)
    samples_by_stratum = defaultdict(list)
    missing_xml = []
    bad_xml = []
    skipped_no_label = []

    # 1. Scan video files
    for video_path in sorted(root.rglob("*")):
        if not video_path.is_file():
            continue
        if video_path.suffix.lower() not in VIDEO_EXTS:
            continue

        xml_path = video_path.with_suffix(".xml")

        if not xml_path.exists():
            if require_xml:
                missing_xml.append(str(video_path))
                continue
            else:
                # Optional behavior: skip videos without XML
                continue

        try:
            meta = parse_xml_annotation(xml_path)
        except Exception as e:
            bad_xml.append((str(xml_path), str(e)))
            continue

        label = meta["label"]
        if not label:
            skipped_no_label.append(str(xml_path))
            continue

        scene_group = infer_scene_group(video_path=video_path, root_dir=root)

        sample = {
            "video_path": str(video_path),
            "xml_path": str(xml_path),
            "label": label,
            "scene_group": scene_group,
            "event_start_time": meta["event_start_time"],
            "event_duration": meta["event_duration"],
            "event_start_sec": meta["event_start_sec"],
            "event_duration_sec": meta["event_duration_sec"],
            "fps": meta["fps"],
            "total_frames": meta["total_frames"],
            "event_start_frame": meta["event_start_frame"],
            "event_end_frame": meta["event_end_frame"],
        }

        samples_by_label[label].append(sample)
        stratum_key = f"{label}__{scene_group}"
        samples_by_stratum[stratum_key].append(sample)

    # 2. Stratified split by (label + scene_group)
    rows = []
    per_stratum_stats = {}

    for stratum_key in sorted(samples_by_stratum):
        samples = sorted(samples_by_stratum[stratum_key], key=lambda x: x["video_path"])
        rng.shuffle(samples)

        n = len(samples)
        n_train, n_val, n_test = compute_split_counts(
            n=n,
            train_ratio=train_ratio,
            val_ratio=val_ratio,
            test_ratio=test_ratio,
        )

        train_samples = samples[:n_train]
        val_samples = samples[n_train:n_train + n_val]
        test_samples = samples[n_train + n_val:]

        for sample in train_samples:
            sample["split"] = "train"
            rows.append(sample)

        for sample in val_samples:
            sample["split"] = "val"
            rows.append(sample)

        for sample in test_samples:
            sample["split"] = "test"
            rows.append(sample)

        per_stratum_stats[stratum_key] = {
            "total": n,
            "train": len(train_samples),
            "val": len(val_samples),
            "test": len(test_samples),
        }

    # 3. Shuffle final rows for nicer CSV ordering
    rng.shuffle(rows)

    # 4. Write CSV
    fieldnames = [
        "video_path",
        "xml_path",
        "label",
        "scene_group",
        "split",
        "event_start_time",
        "event_duration",
        "event_start_sec",
        "event_duration_sec",
        "fps",
        "total_frames",
        "event_start_frame",
        "event_end_frame",
    ]

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    # 5. Print summary
    split_counter = Counter(row["split"] for row in rows)
    split_label_counter = Counter((row["split"], row["label"]) for row in rows)
    split_scene_counter = Counter((row["split"], row["scene_group"]) for row in rows)

    print(f"Saved {len(rows)} samples to {out_path}")
    print(f"Split stats: {dict(split_counter)}")
    print(f"Num labels: {len(samples_by_label)}")

    if samples_by_label:
        print("\nPer-label stats:")
        for label in sorted(samples_by_label):
            total = len([row for row in rows if row["label"] == label])
            train_n = len([row for row in rows if row["label"] == label and row["split"] == "train"])
            val_n = len([row for row in rows if row["label"] == label and row["split"] == "val"])
            test_n = len([row for row in rows if row["label"] == label and row["split"] == "test"])
            print(label, {"total": total, "train": train_n, "val": val_n, "test": test_n})

    if split_label_counter:
        print("\nSplit x Label stats:")
        for key in sorted(split_label_counter):
            split, label = key
            print(f"{split:>5} | {label:<20} -> {split_label_counter[key]}")

    if split_scene_counter:
        print("\nSplit x SceneGroup stats:")
        for key in sorted(split_scene_counter):
            split, scene_group = key
            print(f"{split:>5} | {scene_group:<20} -> {split_scene_counter[key]}")

    if per_stratum_stats:
        print("\nPer-stratum stats (label__scene_group):")
        for stratum_key in sorted(per_stratum_stats):
            print(stratum_key, per_stratum_stats[stratum_key])

    if missing_xml:
        print(f"\n[WARNING] Missing XML for {len(missing_xml)} videos")
        for p in missing_xml[:10]:
            print("  ", p)
        if len(missing_xml) > 10:
            print("   ...")

    if bad_xml:
        print(f"\n[WARNING] Failed to parse {len(bad_xml)} XML files")
        for p, err in bad_xml[:10]:
            print("  ", p, "->", err)
        if len(bad_xml) > 10:
            print("   ...")

    if skipped_no_label:
        print(f"\n[WARNING] XML without label: {len(skipped_no_label)}")
        for p in skipped_no_label[:10]:
            print("  ", p)
        if len(skipped_no_label) > 10:
            print("   ...")


if __name__ == "__main__":
    dataset_tag = "01_assault"
    data_root = Path("../data").resolve()
    raw_root = data_root / "raw"
    processed_root = data_root / "processed"
    raw_dataset_dir = raw_root / "이상행동 CCTV 영상"

    build_manifest_with_split(
        root_dir=str(raw_dataset_dir),
        out_csv=str(processed_root / f"{dataset_tag}_manifest.csv"),
        train_ratio=0.8,
        val_ratio=0.1,
        test_ratio=0.1,
        seed=42,
        require_xml=True,
    )
