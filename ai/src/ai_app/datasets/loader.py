#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 20:14
@Author  : zhangjunfan1997@naver.com
@File    : loader
"""
from __future__ import annotations

import time
from pathlib import Path
from typing import Any

import av
import numpy as np
import pandas as pd
import torch
from torch.utils.data import Dataset, DataLoader
from tqdm import tqdm
from transformers import VideoMAEImageProcessor


# !/usr/bin/env python
# -*- coding: utf-8 -*-


class VideoClipManifestDataset(Dataset):
    """
    Dataset for short pre-extracted video clips.

    External interface is unchanged.
    Internal optimization:
    - do not convert all decoded frames to ndarray
    - only keep selected frames
    - enable PyAV threaded decode
    """

    def __init__(
            self,
            manifest_path: str | Path,
            processor: VideoMAEImageProcessor,
            label2id: dict[str, int] | None = None,
            split: str = "train",
            num_frames: int = 16,
            sampling_rate: int = 4,
            train_random_sampling: bool = True,
    ) -> None:
        super().__init__()

        self.manifest_path = Path(manifest_path)
        self.processor = processor
        self.split = split
        self.num_frames = num_frames
        self.sampling_rate = sampling_rate
        self.clip_len = num_frames * sampling_rate
        self.train_random_sampling = train_random_sampling

        df = pd.read_csv(self.manifest_path)
        df = df[df["split"] == split].reset_index(drop=True)

        if len(df) == 0:
            raise ValueError(f"No samples found for split='{split}' in {manifest_path}")

        required_cols = {"video_path", "label", "split"}
        missing = required_cols - set(df.columns)
        if missing:
            raise ValueError(f"Missing required columns in manifest: {missing}")

        # 比起每次 __getitem__ 用 iloc/Series，提前转 records 更轻
        self.records = df.to_dict("records")

        if label2id is None:
            labels = sorted(df["label"].dropna().unique().tolist())
            self.label2id = {label: idx for idx, label in enumerate(labels)}
        else:
            self.label2id = label2id

        self.id2label = {v: k for k, v in self.label2id.items()}

    def __len__(self) -> int:
        return len(self.records)

    def __getitem__(self, idx: int) -> dict[str, Any]:
        row = self.records[idx]

        video_path = str(row["video_path"])
        label_name = str(row["label"])
        label_id = self.label2id[label_name]

        frames = self._load_and_sample_frames(video_path)
        inputs = self.processor(frames, return_tensors="pt")

        sample = {
            "pixel_values": inputs["pixel_values"].squeeze(0),  # [T, C, H, W]
            "labels": torch.tensor(label_id, dtype=torch.long),
            "label_name": label_name,
            "video_path": video_path,
        }

        for key in [
            "source_video",
            "clip_start_sec",
            "clip_duration",
            "event_start_sec",
            "event_duration_sec",
        ]:
            if key in row and pd.notna(row[key]):
                sample[key] = row[key]

        return sample

    def _load_and_sample_frames(self, video_path: str) -> list[np.ndarray]:
        total_frames = self._probe_total_frames(video_path)

        if total_frames <= 0:
            raise ValueError(f"No frames found in video: {video_path}")

        indices = self._sample_frame_indices(
            total_frames=total_frames,
            is_train=(self.split == "train"),
        )

        sampled_frames = self._decode_selected_frames_pyav(video_path, indices)

        if len(sampled_frames) != len(indices):
            raise ValueError(
                f"Decoded {len(sampled_frames)} frames, but expected {len(indices)} from {video_path}"
            )

        return sampled_frames

    def _sample_frame_indices(
            self,
            total_frames: int,
            is_train: bool,
    ) -> list[int]:
        needed_len = self.clip_len

        if total_frames >= needed_len:
            if is_train and self.train_random_sampling:
                max_offset = total_frames - needed_len
                start_idx = np.random.randint(0, max_offset + 1)
            else:
                start_idx = (total_frames - needed_len) // 2

            indices = start_idx + np.arange(self.num_frames) * self.sampling_rate
            indices = np.clip(indices, 0, total_frames - 1)
            return indices.astype(int).tolist()

        indices = np.linspace(0, total_frames - 1, num=self.num_frames)
        indices = np.clip(np.round(indices).astype(int), 0, total_frames - 1)
        return indices.tolist()

    @staticmethod
    def _probe_total_frames(video_path: str) -> int:
        """
        Fast path: use stream.frames if available.
        Fallback: decode-count only when metadata is missing.
        """
        container = av.open(video_path)
        try:
            stream = container.streams.video[0]
            if stream.frames and stream.frames > 0:
                return int(stream.frames)

            count = 0
            for _ in container.decode(video=0):
                count += 1
            return count
        finally:
            container.close()

    @staticmethod
    def _decode_selected_frames_pyav(video_path: str, indices: list[int]) -> list[np.ndarray]:
        """
        Decode clip sequentially, but only convert requested frames to ndarray.
        This is much cheaper than converting all frames.
        """
        if not indices:
            return []

        target_indices = sorted(set(int(i) for i in indices))
        max_index = target_indices[-1]
        target_set = set(target_indices)

        container = av.open(video_path)
        decoded = {}

        try:
            stream = container.streams.video[0]

            # 开启 FFmpeg/PyAV 的多线程解码
            stream.thread_type = "AUTO"

            for i, frame in enumerate(container.decode(video=0)):
                if i > max_index:
                    break

                if i in target_set:
                    decoded[i] = frame.to_ndarray(format="rgb24")

                    if len(decoded) == len(target_set):
                        break
        finally:
            container.close()

        missing = [i for i in indices if i not in decoded]
        if missing:
            raise ValueError(
                f"Failed to decode target frames {missing[:10]} from video: {video_path}"
            )

        return [decoded[i] for i in indices]


def build_label_mappings(
        manifest_path: str | Path,
) -> tuple[dict[str, int], dict[int, str]]:
    df = pd.read_csv(manifest_path)
    labels = sorted(df["label"].dropna().unique().tolist())
    label2id = {label: idx for idx, label in enumerate(labels)}
    id2label = {idx: label for label, idx in label2id.items()}
    return label2id, id2label


def videomae_collate_fn(batch: list[dict[str, Any]]) -> dict[str, Any]:
    pixel_values = torch.stack([item["pixel_values"] for item in batch])
    labels = torch.stack([item["labels"] for item in batch])

    output = {
        "pixel_values": pixel_values,  # [B, T, C, H, W]
        "labels": labels,  # [B]
    }

    if "label_name" in batch[0]:
        output["label_name"] = [item["label_name"] for item in batch]
    if "video_path" in batch[0]:
        output["video_path"] = [item["video_path"] for item in batch]

    return output


if __name__ == "__main__":
    manifest_path = "../../../data/processed/manifest_clips_all.csv"
    checkpoint = "MCG-NJU/videomae-base-finetuned-kinetics"

    processor = VideoMAEImageProcessor.from_pretrained(checkpoint)
    label2id, id2label = build_label_mappings(manifest_path)

    train_dataset = VideoClipManifestDataset(
        manifest_path=manifest_path,
        processor=processor,
        label2id=label2id,
        split="train",
        num_frames=16,
        sampling_rate=4,
    )

    print("train dataset size:", len(train_dataset))

    # Single Test
    sample = train_dataset[0]
    print(sample.keys())
    print(sample["pixel_values"].shape)
    print(sample["labels"], sample["label_name"])
    print(sample["video_path"])

    # Batch Test
    start = time.time()
    for i in tqdm(range(20)):
        _ = train_dataset[i]
    elapsed = time.time() - start

    print(f"20 samples elapsed: {elapsed:.2f}s")
    print(f"avg per sample: {elapsed / 20:.2f}s")

    # DataLoader Test
    train_loader = DataLoader(
        train_dataset,
        batch_size=4,
        shuffle=True,
        num_workers=0,
        collate_fn=videomae_collate_fn,
    )

    batch = next(iter(train_loader))
    print("batch keys:", batch.keys())
    print("batch pixel_values shape:", batch["pixel_values"].shape)
    print("batch labels shape:", batch["labels"].shape)
    print("batch labels:", batch["labels"])

    # validation set
    val_dataset = VideoClipManifestDataset(
        manifest_path=manifest_path,
        processor=processor,
        label2id=label2id,
        split="val",
        num_frames=16,
        sampling_rate=4,
    )

    print("val dataset size:", len(val_dataset))
