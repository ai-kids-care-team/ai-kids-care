#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-31 17:12
@Author  : zhangjunfan1997@naver.com
@File    : merge_manifests
"""
#!/usr/bin/env python
# -*- coding: utf-8 -*-

from pathlib import Path
import pandas as pd


def merge_manifests(
    manifests_dir: str,
    output_path: str,
    deduplicate: bool = True,
):
    manifests_dir = Path(manifests_dir)
    output_path = Path(output_path)

    csv_files = sorted(manifests_dir.glob("*.csv"))

    if len(csv_files) == 0:
        raise ValueError(f"No CSV files found in {manifests_dir}")

    print(f"Found {len(csv_files)} manifest files")

    dfs = []
    total_rows = 0

    for f in csv_files:
        df = pd.read_csv(f)
        print(f"{f.name}: {len(df)} rows")
        total_rows += len(df)
        dfs.append(df)

    merged_df = pd.concat(dfs, ignore_index=True)

    print(f"\nTotal rows before dedup: {len(merged_df)}")

    if deduplicate:
        before = len(merged_df)

        merged_df = merged_df.drop_duplicates(subset=["video_path"])

        after = len(merged_df)

        print(f"Removed {before - after} duplicates")
        print(f"Total rows after dedup: {after}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    merged_df.to_csv(output_path, index=False, encoding="utf-8")

    print(f"\nSaved merged manifest to: {output_path}")


if __name__ == "__main__":
    merge_manifests(
        manifests_dir="../data/processed/manifests_parts",
        output_path="../data/processed/manifest_clips_all.csv",
        deduplicate=True,
    )