#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Downsample all videos listed in a manifest to a new directory and write
an updated manifest with rewritten video_path.

Full run command (PowerShell):
    cd F:\\ai-kids-care\\ai
    $env:PYTHONPATH='F:\\ai-kids-care\\ai'
    & 'F:\\ai-kids-care\\ai\\.venv\\Scripts\\python.exe' scripts\\downsample_manifest_videos.py `
      --manifest-path data\\processed\\manifest_clips_all.csv `
      --output-dir data\\processed\\event_clips_downsampled `
      --output-manifest data\\processed\\manifest_clips_all_downsampled.csv `
      --shorter-side 320
"""
# !/usr/bin/env python
# -*- coding: utf-8 -*-

from __future__ import annotations

import csv
import hashlib
import os
import shutil
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import pandas as pd
from tqdm import tqdm


def check_ffmpeg_available() -> None:
    ffmpeg_path = shutil.which("ffmpeg")
    if ffmpeg_path is None:
        raise EnvironmentError(
            "ffmpeg not found in PATH. Please install FFmpeg and add it to PATH first."
        )


def build_scale_filter(shorter_side: int) -> str:
    return (
        f"scale=if(gte(iw\\,ih)\\,-2\\,{shorter_side}):"
        f"if(gte(iw\\,ih)\\,{shorter_side}\\,-2)"
    )


def run_ffmpeg_downsample(
        input_path: Path,
        output_path: Path,
        shorter_side: int,
        crf: int,
        preset: str,
        use_gpu: bool,
        use_hwaccel: bool,
        overwrite: bool,
) -> None:
    cmd = ["ffmpeg"]
    cmd.append("-y" if overwrite else "-n")
    if use_hwaccel:
        cmd.extend(["-hwaccel", "cuda"])
    cmd.extend([
        "-i", str(input_path),
        "-vf", build_scale_filter(shorter_side),
    ])

    if use_gpu:
        cmd.extend([
            "-c:v", "h264_nvenc",
            "-preset", "p4",
            "-cq", str(crf),
            "-c:a", "aac",
        ])
    else:
        cmd.extend([
            "-c:v", "libx264",
            "-preset", preset,
            "-crf", str(crf),
            "-c:a", "aac",
        ])

    cmd.extend([
        "-movflags", "+faststart",
        "-loglevel", "error",
        str(output_path),
    ])
    subprocess.run(cmd, check=True)


def resolve_input_path(manifest_path: Path, raw_video_path: str) -> Path:
    p = Path(raw_video_path)
    if p.is_absolute():
        return p
    return (manifest_path.parent / p).resolve()


def infer_common_root(paths: list[Path]) -> Path | None:
    if not paths:
        return None

    if len(paths) == 1:
        return paths[0].parent

    try:
        common = os.path.commonpath([str(p) for p in paths])
    except ValueError:
        return None

    if not common:
        return None

    common_path = Path(common)
    if common_path.suffix:
        return common_path.parent

    return common_path


def build_output_path(
        input_path: Path,
        output_dir: Path,
        split: str | None,
        label: str | None,
        common_root: Path | None,
) -> Path:
    if common_root is not None:
        try:
            relative_path = input_path.relative_to(common_root)
            return output_dir / relative_path
        except ValueError:
            pass

    if split and label:
        base = output_dir / split / label / input_path.name
    else:
        base = output_dir / input_path.name

    suffix = input_path.suffix if input_path.suffix else ".mp4"
    short_hash = hashlib.sha1(str(input_path).encode("utf-8")).hexdigest()[:8]
    return base.with_name(f"{base.stem}_{short_hash}{suffix}")


def downsample_manifest_videos(
        manifest_path: str | Path,
        output_dir: str | Path,
        output_manifest: str | Path,
        shorter_side: int = 320,
        crf: int = 23,
        preset: str = "fast",
        use_gpu: bool = False,
        use_hwaccel: bool = False,
        workers: int = 1,
        overwrite: bool = False,
        limit: int = 0,
) -> None:
    check_ffmpeg_available()

    manifest_path = Path(manifest_path).resolve()
    output_dir = Path(output_dir).resolve()
    output_manifest = Path(output_manifest).resolve()

    output_dir.mkdir(parents=True, exist_ok=True)
    output_manifest.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(manifest_path)
    if len(df) == 0:
        raise ValueError(f"Manifest is empty: {manifest_path}")
    if "video_path" not in df.columns:
        raise ValueError("Manifest missing required column: video_path")

    if limit > 0:
        df = df.iloc[:limit].copy()

    input_paths = [
        resolve_input_path(manifest_path, str(vp))
        for vp in df["video_path"].astype(str).tolist()
    ]
    common_root = infer_common_root(input_paths)

    rows: list[tuple[int, dict]] = []
    failed_rows: list[dict[str, str | int]] = []

    records = list(df.to_dict("records"))
    tasks = list(enumerate(records))

    def process_one(idx: int, row: dict) -> tuple[int, dict | None, dict[str, str | int] | None]:
        input_path = resolve_input_path(manifest_path, str(row["video_path"]))
        split = str(row["split"]) if "split" in row and pd.notna(row["split"]) else None
        label = str(row["label"]) if "label" in row and pd.notna(row["label"]) else None
        output_path = build_output_path(
            input_path=input_path,
            output_dir=output_dir,
            split=split,
            label=label,
            common_root=common_root,
        )
        output_path.parent.mkdir(parents=True, exist_ok=True)

        try:
            if not input_path.is_file():
                raise FileNotFoundError(f"input video not found: {input_path}")

            if overwrite or not output_path.exists():
                run_ffmpeg_downsample(
                    input_path=input_path,
                    output_path=output_path,
                    shorter_side=shorter_side,
                    crf=crf,
                    preset=preset,
                    use_gpu=use_gpu,
                    use_hwaccel=use_hwaccel,
                    overwrite=overwrite,
                )

            new_row = dict(row)
            new_row["video_path"] = str(output_path)
            return idx, new_row, None

        except Exception as e:
            return idx, None, {
                "index": int(idx),
                "video_path": str(input_path),
                "error": str(e),
            }

    workers = max(1, int(workers))
    if workers == 1:
        iterator = tqdm(tasks, total=len(tasks), desc="Downsampling manifest videos")
        for idx, row in iterator:
            done_idx, new_row, failed = process_one(idx, row)
            if new_row is not None:
                rows.append((done_idx, new_row))
            if failed is not None:
                failed_rows.append(failed)
    else:
        with ThreadPoolExecutor(max_workers=workers) as executor:
            futures = [executor.submit(process_one, idx, row) for idx, row in tasks]
            for future in tqdm(as_completed(futures), total=len(futures), desc="Downsampling manifest videos"):
                done_idx, new_row, failed = future.result()
                if new_row is not None:
                    rows.append((done_idx, new_row))
                if failed is not None:
                    failed_rows.append(failed)

    rows.sort(key=lambda x: x[0])
    output_rows = [row for _, row in rows]

    with open(output_manifest, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(df.columns))
        writer.writeheader()
        writer.writerows(output_rows)

    print(f"\nSaved {len(output_rows)} rows to {output_manifest}")
    print(f"Dropped/failed rows: {len(failed_rows)}")
    print(f"Output clips dir: {output_dir}")
    print(f"Downsample shorter side: {shorter_side}")
    print(f"Use GPU encode: {use_gpu}")
    print(f"Use CUDA hwaccel decode: {use_hwaccel}")
    print(f"Workers: {workers}")

    if failed_rows:
        failed_manifest = output_manifest.with_name(output_manifest.stem + "_failed.csv")
        with open(failed_manifest, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=["index", "video_path", "error"])
            writer.writeheader()
            writer.writerows(failed_rows)
        print(f"Saved failed rows to {failed_manifest}")


if __name__ == "__main__":
    data_root = Path("../data/processed").resolve()
    dataset_tag = "06_wander"

    downsample_manifest_videos(
        manifest_path=data_root / f"{dataset_tag}_manifest_clips.csv",
        output_dir=data_root / f"{dataset_tag}_event_clips_downsampled",
        output_manifest=data_root / f"{dataset_tag}_manifest_clips_downsampled.csv",
        shorter_side=540,
        crf=23,
        preset="fast",
        use_gpu=True,
        use_hwaccel=False,
        workers=2,
        overwrite=False,
        limit=0,
    )
