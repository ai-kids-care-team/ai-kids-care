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

        sample = {
            "video_path": str(video_path),
            "xml_path": str(xml_path),
            "label": label,
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

    # 2. Stratified split by label
    rows = []
    per_label_stats = {}

    for label in sorted(samples_by_label):
        samples = sorted(samples_by_label[label], key=lambda x: x["video_path"])
        rng.shuffle(samples)

        n = len(samples)
        n_train = int(n * train_ratio)
        n_val = int(n * val_ratio)
        n_test = n - n_train - n_val

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

        per_label_stats[label] = {
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

    print(f"Saved {len(rows)} samples to {out_path}")
    print(f"Split stats: {dict(split_counter)}")
    print(f"Num labels: {len(samples_by_label)}")

    if per_label_stats:
        print("\nPer-label stats:")
        for label in sorted(per_label_stats):
            print(label, per_label_stats[label])

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
    base_dir = Path(__file__).resolve().parent
    data_dir = (base_dir / "../data").resolve()

    build_manifest_with_split(
        root_dir=str(data_dir / "이상행동 CCTV 영상"),
        out_csv=str(data_dir / "manifest.csv"),
        train_ratio=0.8,
        val_ratio=0.1,
        test_ratio=0.1,
        seed=42,
        require_xml=True,
    )
