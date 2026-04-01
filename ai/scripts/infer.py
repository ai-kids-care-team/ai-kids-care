#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 20:13
@Author  : zhangjunfan1997@naver.com
@File    : infer
"""
# !/usr/bin/env python
# -*- coding: utf-8 -*-

from __future__ import annotations

from pathlib import Path

import av
import numpy as np
import pandas as pd
import torch
from tqdm import tqdm
from transformers import VideoMAEForVideoClassification, VideoMAEImageProcessor


def decode_video_pyav(video_path: str | Path) -> list[np.ndarray]:
    """
    Decode all frames from a short clip using PyAV.
    Returns RGB frames as numpy arrays.
    """
    container = av.open(str(video_path))
    frames = []
    try:
        for frame in container.decode(video=0):
            frames.append(frame.to_ndarray(format="rgb24"))
    finally:
        container.close()
    return frames


def sample_frame_indices(
        total_frames: int,
        num_frames: int = 16,
        sampling_rate: int = 4,
) -> list[int]:
    """
    Same basic strategy as training/eval loader:
    - if enough frames: center sampling with stride
    - otherwise: uniform sampling over the whole clip
    """
    clip_len = num_frames * sampling_rate

    if total_frames <= 0:
        raise ValueError("Video contains no decodable frames.")

    if total_frames >= clip_len:
        start_idx = (total_frames - clip_len) // 2
        indices = start_idx + np.arange(num_frames) * sampling_rate
        indices = np.clip(indices, 0, total_frames - 1)
        return indices.astype(int).tolist()

    indices = np.linspace(0, total_frames - 1, num=num_frames)
    indices = np.clip(np.round(indices).astype(int), 0, total_frames - 1)
    return indices.tolist()


def predict_video(
        video_path: str | Path,
        model_dir: str | Path,
        num_frames: int = 16,
        sampling_rate: int = 4,
) -> None:
    model_dir = Path(model_dir)
    video_path = Path(video_path)

    if not model_dir.exists():
        raise FileNotFoundError(f"Model dir not found: {model_dir}")
    if not video_path.exists():
        raise FileNotFoundError(f"Video not found: {video_path}")

    device = "cuda" if torch.cuda.is_available() else "cpu"

    processor = VideoMAEImageProcessor.from_pretrained(model_dir)
    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    model.to(device)
    model.eval()

    frames = decode_video_pyav(video_path)
    total_frames = len(frames)
    indices = sample_frame_indices(
        total_frames=total_frames,
        num_frames=num_frames,
        sampling_rate=sampling_rate,
    )
    sampled_frames = [frames[i] for i in indices]

    inputs = processor(sampled_frames, return_tensors="pt")
    pixel_values = inputs["pixel_values"].to(device)

    with torch.no_grad():
        outputs = model(pixel_values=pixel_values)
        logits = outputs.logits
        probs = torch.softmax(logits, dim=-1)[0].cpu().numpy()

    pred_id = int(np.argmax(probs))
    pred_label = model.config.id2label[str(pred_id)] if str(pred_id) in model.config.id2label else \
        model.config.id2label[pred_id]

    print(f"video_path: {video_path}")
    print(f"pred_id: {pred_id}")
    print(f"pred_label: {pred_label}")
    print("\nclass probabilities:")
    for i, p in enumerate(probs):
        label = model.config.id2label[str(i)] if str(i) in model.config.id2label else model.config.id2label[i]
        print(f"  {label}: {p:.6f}")


def evaluate_test_split(
        model_dir: str | Path,
        manifest_path: str | Path,
        num_frames: int = 16,
        sampling_rate: int = 4,
) -> None:
    model_dir = Path(model_dir)
    manifest_path = Path(manifest_path)

    if not model_dir.exists():
        raise FileNotFoundError(f"Model dir not found: {model_dir}")
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")

    df = pd.read_csv(manifest_path)
    required_cols = {"video_path", "label", "split"}
    missing = required_cols - set(df.columns)
    if missing:
        raise ValueError(f"Missing required columns in manifest: {missing}")

    test_df = df[df["split"] == "test"].reset_index(drop=True)
    if len(test_df) == 0:
        raise ValueError(f"No test samples in manifest: {manifest_path}")

    device = "cuda" if torch.cuda.is_available() else "cpu"

    processor = VideoMAEImageProcessor.from_pretrained(model_dir)
    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    model.to(device)
    model.eval()

    correct = 0
    total = len(test_df)
    failed = 0

    with torch.no_grad():
        for row in tqdm(test_df.to_dict("records"), total=total, desc="Evaluating test split"):
            video_path = Path(str(row["video_path"]))
            true_label = str(row["label"])

            try:
                frames = decode_video_pyav(video_path)
                indices = sample_frame_indices(
                    total_frames=len(frames),
                    num_frames=num_frames,
                    sampling_rate=sampling_rate,
                )
                sampled_frames = [frames[i] for i in indices]
                inputs = processor(sampled_frames, return_tensors="pt")
                pixel_values = inputs["pixel_values"].to(device)

                outputs = model(pixel_values=pixel_values)
                pred_id = int(torch.argmax(outputs.logits, dim=-1)[0].item())
                pred_label = model.config.id2label[str(pred_id)] if str(pred_id) in model.config.id2label else \
                    model.config.id2label[pred_id]
            except Exception as e:
                failed += 1
                print(f"[WARN] failed sample: {video_path} ({type(e).__name__}: {e})")
                continue

            if pred_label == true_label:
                correct += 1

    evaluated = total - failed
    accuracy_evaluated = correct / evaluated if evaluated > 0 else 0.0
    accuracy_total = correct / total if total > 0 else 0.0

    print("\n===== Test Summary =====")
    print(f"manifest_path: {manifest_path}")
    print(f"model_dir: {model_dir}")
    print(f"total_test_samples: {total}")
    print(f"evaluated_samples: {evaluated}")
    print(f"failed_samples: {failed}")
    print(f"correct_predictions: {correct}")
    print(f"accuracy_evaluated: {accuracy_evaluated:.6f}")
    print(f"accuracy_total: {accuracy_total:.6f}")


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parent.parent

    model_dir = project_root / "outputs" / "videomae_baseline" / "best_model"
    manifest_candidates = [
        project_root / "data" / "processed" / "manifest_clips_all_downsampled.csv",
    ]

    manifest_path = None
    for candidate in manifest_candidates:
        if candidate.exists():
            manifest_path = candidate
            break

    if manifest_path is None:
        raise FileNotFoundError(
            "No manifest found. Checked: "
            + ", ".join(str(p) for p in manifest_candidates)
        )

    evaluate_test_split(
        model_dir=model_dir,
        manifest_path=manifest_path,
        num_frames=16,
        sampling_rate=4,
    )
