#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Rebuild clip manifests from current clip directories.
"""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path

import pandas as pd


def _normalize_clip_root(clip_root: str | Path) -> Path:
    return Path(clip_root).resolve()


def _infer_split_and_label_from_path(clip_path: Path, root_dir: Path) -> tuple[str | None, str | None]:
    try:
        rel = clip_path.relative_to(root_dir)
    except ValueError:
        return None, None

    if len(rel.parts) < 3:
        return None, None

    split = rel.parts[0]
    label = rel.parts[1]
    if split not in {"train", "val", "test"}:
        return None, None
    return split, label


def _load_reference_metadata(
        reference_manifests: list[Path],
) -> dict[str, list[dict]]:
    metadata_index: dict[str, list[dict]] = defaultdict(list)
    for manifest_path in reference_manifests:
        df = pd.read_csv(manifest_path)
        if "video_path" not in df.columns:
            continue
        for row in df.to_dict("records"):
            key = Path(str(row["video_path"])).name.lower()
            metadata_index[key].append(row)
    return metadata_index


def _pick_reference_row(
        rows: list[dict],
        split: str,
        label: str,
) -> dict | None:
    if not rows:
        return None
    for one_row in rows:
        if str(one_row.get("split", "")) == split and str(one_row.get("label", "")) == label:
            return one_row
    return rows[0]


def _clean_cell_value(value):
    if pd.isna(value):
        return ""
    return value


def rebuild_manifest_for_clip_dir(
        clip_root: str | Path,
        output_manifest: str | Path,
        reference_manifests: list[str | Path] | None = None,
        negative_label: str = "normal",
) -> Path:
    clip_root = _normalize_clip_root(clip_root)
    output_manifest = Path(output_manifest).resolve()
    output_manifest.parent.mkdir(parents=True, exist_ok=True)

    if not clip_root.exists():
        raise FileNotFoundError(f"Clip root not found: {clip_root}")

    resolved_reference_manifests: list[Path] = []
    if reference_manifests:
        for one_manifest in reference_manifests:
            one_manifest_path = Path(one_manifest).resolve()
            if one_manifest_path.exists():
                resolved_reference_manifests.append(one_manifest_path)
            else:
                print(f"[WARN] reference manifest not found, skip: {one_manifest_path}")

    metadata_index: dict[str, list[dict]] = {}
    if resolved_reference_manifests:
        metadata_index = _load_reference_metadata(resolved_reference_manifests)

    rows: list[dict] = []
    skipped_rows: list[dict] = []
    metadata_hit_count = 0
    allowed_exts = {".mp4", ".avi", ".mov", ".mkv", ".webm"}

    for clip_path in sorted(clip_root.rglob("*")):
        if not clip_path.is_file():
            continue
        if clip_path.suffix.lower() not in allowed_exts:
            continue

        split, label = _infer_split_and_label_from_path(clip_path, clip_root)
        if split is None or label is None:
            skipped_rows.append({
                "video_path": str(clip_path),
                "error": "Cannot infer split/label from path. Expected <root>/<split>/<label>/<file>.",
            })
            continue

        reference_row = None
        if metadata_index:
            key = clip_path.name.lower()
            reference_row = _pick_reference_row(metadata_index.get(key, []), split=split, label=label)
            if reference_row is not None:
                metadata_hit_count += 1

        if reference_row is None:
            clip_role = "negative" if label == negative_label else "positive"
            negative_source_type = ""
            source_video = ""
            clip_start_sec = ""
            clip_duration = ""
            event_start_sec = ""
            event_duration_sec = ""
        else:
            clip_role = str(_clean_cell_value(reference_row.get("clip_role", "")))
            if not clip_role:
                clip_role = "negative" if label == negative_label else "positive"
            negative_source_type = str(_clean_cell_value(reference_row.get("negative_source_type", "")))
            source_video = _clean_cell_value(reference_row.get("source_video", ""))
            clip_start_sec = _clean_cell_value(reference_row.get("clip_start_sec", ""))
            clip_duration = _clean_cell_value(reference_row.get("clip_duration", ""))
            event_start_sec = _clean_cell_value(reference_row.get("event_start_sec", ""))
            event_duration_sec = _clean_cell_value(reference_row.get("event_duration_sec", ""))

        rows.append({
            "video_path": str(clip_path),
            "label": label,
            "split": split,
            "source_video": source_video,
            "clip_start_sec": clip_start_sec,
            "clip_duration": clip_duration,
            "event_start_sec": event_start_sec,
            "event_duration_sec": event_duration_sec,
            "clip_role": clip_role,
            "negative_source_type": negative_source_type,
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
    rebuilt_df = pd.DataFrame(rows, columns=fieldnames)
    if len(rebuilt_df) > 0:
        rebuilt_df = rebuilt_df.drop_duplicates(subset=["video_path"], keep="first")
        rebuilt_df = rebuilt_df.sort_values(["split", "label", "video_path"]).reset_index(drop=True)

    rebuilt_df.to_csv(output_manifest, index=False, encoding="utf-8")
    print(f"Saved rebuilt manifest: {output_manifest}")
    print(f"Rebuilt rows: {len(rebuilt_df)}")
    if resolved_reference_manifests:
        print(f"Reference metadata hits: {metadata_hit_count}")

    if skipped_rows:
        skipped_path = output_manifest.with_name(output_manifest.stem + "_rebuild_skipped.csv")
        pd.DataFrame(skipped_rows).to_csv(skipped_path, index=False, encoding="utf-8")
        print(f"Saved skipped rows: {skipped_path} ({len(skipped_rows)})")

    return output_manifest


if __name__ == "__main__":
    dataset_tag = "01_assault"
    data_root = Path("../data/processed")

    main_clip_root = data_root / f"{dataset_tag}_event_clips"
    main_manifest_path = data_root / f"{dataset_tag}_manifest_clips.csv"
    candidate_manifest_path = data_root / f"{dataset_tag}_manifest_clips_candidates.csv"

    rebuild_manifest_for_clip_dir(
        clip_root=main_clip_root,
        output_manifest=main_manifest_path,
        reference_manifests=[main_manifest_path, candidate_manifest_path],
        negative_label="normal",
    )
