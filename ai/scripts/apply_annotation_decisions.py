#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Apply annotation decisions to main/candidate clip groups.

Decision rules:
1) main_error_labeled == 1
   -> delete this group from main clips
2) candidate_error_labeled != 1
   -> move this candidate group into main clips (introduce)
3) candidate_error_labeled == 1
   -> reject candidate group (delete from candidate clips)

After file operations, manifests are rebuilt from actual clip directories.
"""

from __future__ import annotations

import shutil
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import pandas as pd
from tqdm import tqdm

from rebuild_clip_manifests import rebuild_main_and_candidate_manifests


def _normalize_text(value) -> str:
    if pd.isna(value):
        return ""
    return str(value).strip()


def _is_one(value) -> bool:
    text = _normalize_text(value)
    if text == "":
        return False
    lower = text.lower()
    if lower in {"1", "true", "t", "yes", "y"}:
        return True
    try:
        return float(text) == 1.0
    except Exception:
        return False


def _is_not_one(value) -> bool:
    return not _is_one(value)


def _group_key_from_row(row: pd.Series) -> tuple[str, str, str, str, str]:
    return (
        _normalize_text(row["split"]),
        _normalize_text(row["label"]),
        _normalize_text(row["clip_role"]),
        _normalize_text(row["event_group_id"]),
        _normalize_text(row["negative_source_type"]),
    )


def _manifest_group_key_df(df: pd.DataFrame) -> pd.DataFrame:
    required_cols = {
        "video_path",
        "split",
        "label",
        "clip_role",
        "negative_source_type",
    }
    missing = required_cols - set(df.columns)
    if missing:
        raise ValueError(f"Missing required columns in manifest: {missing}")

    out = df.copy()
    file_names = out["video_path"].astype(str).map(lambda p: Path(p).stem)
    event_group_id = (
        file_names.str.replace(r"_(?:cand_)?pos\d+_neg\d+_.+$", "", regex=True)
        .str.replace(r"_(?:cand_)?pos\d+$", "", regex=True)
    )
    out["_group_key"] = list(
        zip(
            out["split"].astype(str),
            out["label"].astype(str),
            out["clip_role"].astype(str),
            event_group_id.astype(str),
            out.apply(
                lambda r: _normalize_text(r["negative_source_type"]) if _normalize_text(r["clip_role"]) == "negative" else "",
                axis=1,
            ).astype(str),
        )
    )
    return out


def _safe_delete_file(path: Path, dry_run: bool) -> tuple[bool, str]:
    if not path.exists():
        return False, "missing"
    if dry_run:
        return True, "dry_run"
    try:
        path.unlink()
        return True, "deleted"
    except Exception as e:
        return False, f"delete_failed: {e}"


def _safe_move_file(src: Path, dst: Path, dry_run: bool) -> tuple[Path, str]:
    if not src.exists():
        return dst, "source_missing"

    target = dst
    if target.exists():
        stem = target.stem
        suffix = target.suffix
        parent = target.parent
        i = 1
        while True:
            candidate = parent / f"{stem}_moved{i}{suffix}"
            if not candidate.exists():
                target = candidate
                break
            i += 1

    if dry_run:
        return target, "dry_run"

    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(target))
    return target, "moved"


def _backup_file(file_path: Path, dry_run: bool) -> Path | None:
    if not file_path.exists():
        return None
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = file_path.with_name(file_path.stem + f".bak_{ts}" + file_path.suffix)
    if dry_run:
        return backup_path
    shutil.copy2(file_path, backup_path)
    return backup_path


@dataclass
class ApplyResult:
    dry_run: bool
    invalid_main_groups: int
    promote_candidate_groups: int
    reject_candidate_groups: int
    main_delete_targets: int
    main_delete_success: int
    candidate_reject_targets: int
    candidate_reject_success: int
    candidate_promote_targets: int
    candidate_promote_success: int
    candidate_unselected_targets: int
    candidate_unselected_success: int


def apply_annotation_decisions(
        annotation_excel_path: str | Path,
        main_manifest_path: str | Path,
        candidate_manifest_path: str | Path,
        main_label_column: str = "main_error_labeled",
        candidate_label_column: str = "candidate_error_labeled",
        dry_run: bool = True,
        report_csv_path: str | Path | None = None,
) -> ApplyResult:
    annotation_excel_path = Path(annotation_excel_path).resolve()
    main_manifest_path = Path(main_manifest_path).resolve()
    candidate_manifest_path = Path(candidate_manifest_path).resolve()

    if not annotation_excel_path.exists():
        raise FileNotFoundError(f"Annotation excel not found: {annotation_excel_path}")
    if not main_manifest_path.exists():
        raise FileNotFoundError(f"Main manifest not found: {main_manifest_path}")
    if not candidate_manifest_path.exists():
        raise FileNotFoundError(f"Candidate manifest not found: {candidate_manifest_path}")

    annotation_df = pd.read_excel(annotation_excel_path)
    required_annotation_cols = {
        "split",
        "label",
        "clip_role",
        "event_group_id",
        "negative_source_type",
        "main_video_path",
        "candidate_video_path",
        main_label_column,
        candidate_label_column,
    }
    missing_annotation_cols = required_annotation_cols - set(annotation_df.columns)
    if missing_annotation_cols:
        raise ValueError(f"Missing required columns in annotation excel: {missing_annotation_cols}")

    annotation_df["_group_key"] = annotation_df.apply(_group_key_from_row, axis=1)
    annotation_df["_main_invalid"] = annotation_df[main_label_column].map(_is_one)
    annotation_df["_candidate_promote"] = annotation_df[candidate_label_column].map(_is_not_one)
    annotation_df["_candidate_reject"] = annotation_df[candidate_label_column].map(_is_one)

    invalid_main_keys = set(annotation_df.loc[annotation_df["_main_invalid"], "_group_key"].tolist())
    promote_candidate_keys = set(annotation_df.loc[annotation_df["_candidate_promote"], "_group_key"].tolist())
    reject_candidate_keys = set(annotation_df.loc[annotation_df["_candidate_reject"], "_group_key"].tolist())

    main_df = _manifest_group_key_df(pd.read_csv(main_manifest_path))
    cand_df = _manifest_group_key_df(pd.read_csv(candidate_manifest_path))

    main_clip_root = main_manifest_path.with_name(main_manifest_path.stem.replace("_manifest_clips", "_event_clips"))
    candidate_clip_root = candidate_manifest_path.with_name(
        candidate_manifest_path.stem.replace("_manifest_clips_candidates", "_event_clips_candidates")
    )
    if not main_clip_root.exists():
        fallback_root = main_manifest_path.parent / (main_manifest_path.stem.replace("_manifest_clips", "_event_clips"))
        main_clip_root = fallback_root
    if not candidate_clip_root.exists():
        fallback_candidate_root = candidate_manifest_path.parent / (
            candidate_manifest_path.stem.replace("_manifest_clips_candidates", "_event_clips_candidates")
        )
        candidate_clip_root = fallback_candidate_root

    # Operation targets
    main_delete_rows = main_df[main_df["_group_key"].isin(invalid_main_keys)].copy()
    candidate_promote_rows = cand_df[cand_df["_group_key"].isin(promote_candidate_keys)].copy()
    candidate_reject_rows = cand_df[cand_df["_group_key"].isin(reject_candidate_keys)].copy()

    # Reject set should not overlap promoted set; reject wins.
    if len(candidate_reject_rows) > 0 and len(candidate_promote_rows) > 0:
        reject_keys = set(candidate_reject_rows["_group_key"].tolist())
        candidate_promote_rows = candidate_promote_rows[~candidate_promote_rows["_group_key"].isin(reject_keys)].copy()

    operation_rows = []

    main_delete_unique_rows = main_delete_rows.drop_duplicates(subset=["video_path"])
    main_delete_success = 0
    for _, row in tqdm(
        main_delete_unique_rows.iterrows(),
        total=len(main_delete_unique_rows),
        desc="Delete main invalid",
    ):
        path = Path(str(row["video_path"]))
        ok, status = _safe_delete_file(path, dry_run=dry_run)
        if ok:
            main_delete_success += 1
        operation_rows.append({
            "action": "delete_main_invalid",
            "video_path": str(path),
            "status": status,
            "group_key": str(row["_group_key"]),
        })

    candidate_reject_unique_rows = candidate_reject_rows.drop_duplicates(subset=["video_path"])
    candidate_reject_success = 0
    for _, row in tqdm(
        candidate_reject_unique_rows.iterrows(),
        total=len(candidate_reject_unique_rows),
        desc="Delete candidate reject",
    ):
        path = Path(str(row["video_path"]))
        ok, status = _safe_delete_file(path, dry_run=dry_run)
        if ok:
            candidate_reject_success += 1
        operation_rows.append({
            "action": "delete_candidate_reject",
            "video_path": str(path),
            "status": status,
            "group_key": str(row["_group_key"]),
        })

    candidate_promote_unique_rows = candidate_promote_rows.drop_duplicates(subset=["video_path"])
    candidate_promote_success = 0
    for _, row in tqdm(
        candidate_promote_unique_rows.iterrows(),
        total=len(candidate_promote_unique_rows),
        desc="Promote candidate",
    ):
        src = Path(str(row["video_path"]))
        rel = None
        try:
            rel = src.relative_to(candidate_clip_root)
        except Exception:
            pass
        dst = main_clip_root / rel if rel is not None else (main_clip_root / src.name)
        moved_path, status = _safe_move_file(src, dst, dry_run=dry_run)
        if status in {"moved", "dry_run"}:
            candidate_promote_success += 1
        operation_rows.append({
            "action": "promote_candidate_to_main",
            "video_path": str(src),
            "target_video_path": str(moved_path),
            "status": status,
            "group_key": str(row["_group_key"]),
        })

    # Delete all unselected candidate clips so candidate directory doesn't keep leftovers.
    selected_candidate_paths = set(candidate_promote_rows["video_path"].astype(str).tolist()) | set(
        candidate_reject_rows["video_path"].astype(str).tolist()
    )
    candidate_unselected_rows = cand_df[~cand_df["video_path"].astype(str).isin(selected_candidate_paths)].copy()

    candidate_unselected_unique_rows = candidate_unselected_rows.drop_duplicates(subset=["video_path"])
    candidate_unselected_success = 0
    for _, row in tqdm(
        candidate_unselected_unique_rows.iterrows(),
        total=len(candidate_unselected_unique_rows),
        desc="Delete unselected candidate",
    ):
        path = Path(str(row["video_path"]))
        ok, status = _safe_delete_file(path, dry_run=dry_run)
        if ok:
            candidate_unselected_success += 1
        operation_rows.append({
            "action": "delete_candidate_unselected",
            "video_path": str(path),
            "status": status,
            "group_key": str(row["_group_key"]),
        })

    # Backup manifests before rebuild.
    if not dry_run:
        _backup_file(main_manifest_path, dry_run=False)
        _backup_file(candidate_manifest_path, dry_run=False)

        rebuild_main_and_candidate_manifests(
            main_clip_root=main_clip_root,
            candidate_clip_root=candidate_clip_root,
            main_manifest_path=main_manifest_path,
            candidate_manifest_path=candidate_manifest_path,
            reference_manifests=[main_manifest_path, candidate_manifest_path],
            negative_label="normal",
        )

    if report_csv_path is None:
        report_csv_path = annotation_excel_path.with_name(annotation_excel_path.stem + "_apply_report.csv")
    report_csv_path = Path(report_csv_path).resolve()
    pd.DataFrame(operation_rows).to_csv(report_csv_path, index=False, encoding="utf-8")

    result = ApplyResult(
        dry_run=dry_run,
        invalid_main_groups=len(invalid_main_keys),
        promote_candidate_groups=len(promote_candidate_keys - reject_candidate_keys),
        reject_candidate_groups=len(reject_candidate_keys),
        main_delete_targets=main_delete_rows["video_path"].nunique(),
        main_delete_success=main_delete_success,
        candidate_reject_targets=candidate_reject_rows["video_path"].nunique(),
        candidate_reject_success=candidate_reject_success,
        candidate_promote_targets=candidate_promote_rows["video_path"].nunique(),
        candidate_promote_success=candidate_promote_success,
        candidate_unselected_targets=candidate_unselected_rows["video_path"].nunique(),
        candidate_unselected_success=candidate_unselected_success,
    )

    print("\n===== Apply Annotation Decisions Summary =====")
    print(f"dry_run: {result.dry_run}")
    print(f"annotation_excel: {annotation_excel_path}")
    print(f"main_manifest: {main_manifest_path}")
    print(f"candidate_manifest: {candidate_manifest_path}")
    print(f"invalid_main_groups(main_error_labeled=1): {result.invalid_main_groups}")
    print(f"promote_candidate_groups(candidate_error_labeled!=1): {result.promote_candidate_groups}")
    print(f"reject_candidate_groups(candidate_error_labeled=1): {result.reject_candidate_groups}")
    print(f"main_delete_targets: {result.main_delete_targets}, success: {result.main_delete_success}")
    print(f"candidate_reject_targets: {result.candidate_reject_targets}, success: {result.candidate_reject_success}")
    print(f"candidate_promote_targets: {result.candidate_promote_targets}, success: {result.candidate_promote_success}")
    print(
        f"candidate_unselected_targets: {result.candidate_unselected_targets}, "
        f"success: {result.candidate_unselected_success}"
    )
    print(f"report_csv: {report_csv_path}")

    if dry_run:
        print("\n[INFO] This was a dry run. No files or manifests were changed.")
        print("[INFO] Set dry_run=False in __main__ to execute file operations + manifest rebuild.")

    return result


if __name__ == "__main__":
    dataset_tag = "01_assault"
    data_root = Path("../data/processed")

    annotation_excel_path = data_root / f"{dataset_tag}_manifest_clips_annotation.xlsx"
    main_manifest_path = data_root / f"{dataset_tag}_manifest_clips.csv"
    candidate_manifest_path = data_root / f"{dataset_tag}_manifest_clips_candidates.csv"
    report_csv_path = data_root / f"{dataset_tag}_manifest_clips_annotation_apply_report.csv"

    # Safety first: preview without touching files.
    dry_run = False

    apply_annotation_decisions(
        annotation_excel_path=annotation_excel_path,
        main_manifest_path=main_manifest_path,
        candidate_manifest_path=candidate_manifest_path,
        main_label_column="main_error_labeled",
        candidate_label_column="candidate_error_labeled",
        dry_run=dry_run,
        report_csv_path=report_csv_path,
    )
