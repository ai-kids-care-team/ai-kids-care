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

from functools import lru_cache
from pathlib import Path

import av
import numpy as np
import pandas as pd
import torch
from sklearn.metrics import precision_recall_fscore_support, confusion_matrix
from tqdm import tqdm
from torch.utils.data import DataLoader, Dataset
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


@lru_cache(maxsize=50000)
def probe_total_frames(video_path: str) -> int:
    container = av.open(video_path)
    try:
        if not container.streams.video:
            raise ValueError(f"No video stream found: {video_path}")
        stream = container.streams.video[0]
        if stream.frames and stream.frames > 0:
            return int(stream.frames)

        count = 0
        for _ in container.decode(video=0):
            count += 1
        return count
    finally:
        container.close()


def decode_selected_frames_pyav(
        video_path: str | Path,
        indices: list[int],
        decode_thread_type: str | None = "AUTO",
) -> list[np.ndarray]:
    if not indices:
        return []

    video_path = str(video_path)
    target_indices = sorted(set(int(i) for i in indices))
    target_set = set(target_indices)
    max_index = target_indices[-1]

    container = av.open(video_path)
    decoded = {}

    try:
        if not container.streams.video:
            raise ValueError(f"No video stream found: {video_path}")
        stream = container.streams.video[0]
        if decode_thread_type:
            stream.thread_type = decode_thread_type

        for i, frame in enumerate(container.decode(video=0)):
            if i > max_index:
                break
            if i in target_set:
                decoded[i] = frame.to_ndarray(format="rgb24")
                if len(decoded) == len(target_set):
                    break
            del frame
    finally:
        container.close()

    missing = [i for i in indices if i not in decoded]
    if missing:
        raise ValueError(f"Failed to decode target frames {missing[:10]} from video: {video_path}")

    return [decoded[i] for i in indices]


class TestManifestDataset(Dataset):
    def __init__(
            self,
            manifest_path: str | Path,
            processor: VideoMAEImageProcessor,
            num_frames: int = 16,
            sampling_rate: int = 4,
            decode_thread_type: str | None = "AUTO",
    ) -> None:
        self.manifest_path = Path(manifest_path)
        self.processor = processor
        self.num_frames = num_frames
        self.sampling_rate = sampling_rate
        self.decode_thread_type = decode_thread_type

        df = pd.read_csv(self.manifest_path)
        required_cols = {"video_path", "label", "split"}
        missing = required_cols - set(df.columns)
        if missing:
            raise ValueError(f"Missing required columns in manifest: {missing}")

        self.df = df[df["split"] == "test"].reset_index(drop=True)
        if len(self.df) == 0:
            raise ValueError(f"No test samples in manifest: {manifest_path}")

        self.records = self.df.to_dict("records")

    def __len__(self) -> int:
        return len(self.records)

    def __getitem__(self, idx: int) -> dict:
        row = self.records[idx]
        video_path = str(row["video_path"])
        label_name = str(row["label"])

        try:
            total_frames = probe_total_frames(video_path)
            indices = sample_frame_indices(
                total_frames=total_frames,
                num_frames=self.num_frames,
                sampling_rate=self.sampling_rate,
            )
            sampled_frames = decode_selected_frames_pyav(
                video_path=video_path,
                indices=indices,
                decode_thread_type=self.decode_thread_type,
            )
            inputs = self.processor(sampled_frames, return_tensors="pt")
            return {
                "pixel_values": inputs["pixel_values"].squeeze(0),  # [T, C, H, W]
                "label_name": label_name,
                "video_path": video_path,
                "error": None,
            }
        except Exception as e:
            return {
                "pixel_values": None,
                "label_name": label_name,
                "video_path": video_path,
                "error": f"{type(e).__name__}: {e}",
            }


def infer_collate_fn(batch: list[dict]) -> dict:
    valid_items = [item for item in batch if item["pixel_values"] is not None]
    failed_items = [item for item in batch if item["pixel_values"] is None]

    if not valid_items:
        return {
            "pixel_values": None,
            "label_name": [],
            "video_path": [],
            "failed_items": failed_items,
        }

    pixel_values = torch.stack([item["pixel_values"] for item in valid_items])
    label_names = [item["label_name"] for item in valid_items]
    video_paths = [item["video_path"] for item in valid_items]

    return {
        "pixel_values": pixel_values,
        "label_name": label_names,
        "video_path": video_paths,
        "failed_items": failed_items,
    }


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
        batch_size: int = 8,
        num_workers: int = 6,
        prefetch_factor: int = 2,
        decode_thread_type: str | None = "AUTO",
        export_dir: str | Path | None = None,
) -> dict[str, float | int | str]:
    model_dir = Path(model_dir)
    manifest_path = Path(manifest_path)

    if not model_dir.exists():
        raise FileNotFoundError(f"Model dir not found: {model_dir}")
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")

    device = "cuda" if torch.cuda.is_available() else "cpu"
    use_cuda = device == "cuda"
    if use_cuda:
        torch.backends.cudnn.benchmark = True

    processor = VideoMAEImageProcessor.from_pretrained(model_dir)
    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    model.to(device)
    model.eval()

    dataset = TestManifestDataset(
        manifest_path=manifest_path,
        processor=processor,
        num_frames=num_frames,
        sampling_rate=sampling_rate,
        decode_thread_type=decode_thread_type,
    )
    total = len(dataset)
    num_workers = max(0, int(num_workers))

    dataloader_kwargs = {
        "dataset": dataset,
        "batch_size": batch_size,
        "shuffle": False,
        "num_workers": num_workers,
        "pin_memory": use_cuda,
        "persistent_workers": (num_workers > 0),
        "collate_fn": infer_collate_fn,
    }
    if num_workers > 0:
        dataloader_kwargs["prefetch_factor"] = prefetch_factor

    dataloader = DataLoader(**dataloader_kwargs)

    correct = 0
    failed = 0
    failed_log_limit = 10
    y_true: list[str] = []
    y_pred: list[str] = []
    evaluated_rows: list[dict[str, str | int]] = []

    with torch.inference_mode():
        for batch in tqdm(dataloader, total=len(dataloader), desc=f"Evaluating {model_dir.name}"):
            failed_items = batch["failed_items"]
            failed += len(failed_items)
            if failed_items and failed_log_limit > 0:
                for item in failed_items[:failed_log_limit]:
                    print(f"[WARN] failed sample: {item['video_path']} ({item['error']})")
                failed_log_limit -= min(failed_log_limit, len(failed_items))

            if batch["pixel_values"] is None:
                continue

            pixel_values = batch["pixel_values"].to(device, non_blocking=use_cuda)
            outputs = model(pixel_values=pixel_values)
            pred_ids = torch.argmax(outputs.logits, dim=-1).detach().cpu().tolist()

            for pred_id, true_label, video_path in zip(
                    pred_ids,
                    batch["label_name"],
                    batch["video_path"],
            ):
                pred_label = model.config.id2label[str(pred_id)] if str(pred_id) in model.config.id2label else \
                    model.config.id2label[pred_id]
                y_true.append(true_label)
                y_pred.append(pred_label)
                evaluated_rows.append({
                    "video_path": str(video_path),
                    "true_label": str(true_label),
                    "pred_label": str(pred_label),
                    "is_correct": int(pred_label == true_label),
                })
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
    print(f"infer_batch_size: {batch_size}")
    print(f"infer_num_workers: {num_workers}")
    print(f"infer_prefetch_factor: {prefetch_factor if num_workers > 0 else 'N/A'}")
    print(f"decode_thread_type: {decode_thread_type}")

    if export_dir is not None and evaluated > 0:
        export_dir = Path(export_dir)
        export_dir.mkdir(parents=True, exist_ok=True)

        label_items = []
        for k, v in model.config.label2id.items():
            try:
                label_items.append((int(v), str(k)))
            except Exception:
                continue
        label_items.sort(key=lambda x: x[0])
        labels = [name for _, name in label_items]
        extra_labels = sorted(set(y_true).union(set(y_pred)) - set(labels))
        labels.extend(extra_labels)

        cm = confusion_matrix(y_true, y_pred, labels=labels)
        cm_df = pd.DataFrame(cm, index=labels, columns=labels)
        cm_norm_df = cm_df.div(cm_df.sum(axis=1).replace(0, np.nan), axis=0).fillna(0.0)

        precision, recall, f1, support = precision_recall_fscore_support(
            y_true,
            y_pred,
            labels=labels,
            zero_division=0,
        )
        per_class_df = pd.DataFrame({
            "label": labels,
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": support,
        })

        cm_path = export_dir / "confusion_matrix.csv"
        cm_norm_path = export_dir / "confusion_matrix_normalized.csv"
        per_class_path = export_dir / "per_class_metrics.csv"
        misclassified_path = export_dir / "misclassified_samples.csv"
        cm_df.to_csv(cm_path, encoding="utf-8")
        cm_norm_df.to_csv(cm_norm_path, encoding="utf-8")
        per_class_df.to_csv(per_class_path, index=False, encoding="utf-8")
        misclassified_df = pd.DataFrame(
            [row for row in evaluated_rows if row["is_correct"] == 0],
            columns=["video_path", "true_label", "pred_label", "is_correct"],
        )
        misclassified_df.to_csv(misclassified_path, index=False, encoding="utf-8")

        print("\n===== Exported Metrics =====")
        print(f"confusion_matrix: {cm_path}")
        print(f"confusion_matrix_normalized: {cm_norm_path}")
        print(f"per_class_metrics: {per_class_path}")
        print(f"misclassified_samples: {misclassified_path}")

    return {
        "model_dir": str(model_dir),
        "total_test_samples": total,
        "evaluated_samples": evaluated,
        "failed_samples": failed,
        "correct_predictions": correct,
        "accuracy_evaluated": accuracy_evaluated,
        "accuracy_total": accuracy_total,
    }


if __name__ == "__main__":
    dataset_tag = "01_assault"
    project_root = Path(__file__).resolve().parent.parent
    best_model_dir = project_root / "outputs" / f"{dataset_tag}_videomae_baseline" / "best_model"
    metrics_export_dir = project_root / "outputs" / "predictions" / f"{dataset_tag}_best_model_test_metrics"
    manifest_path = project_root / "data" / "processed" / f"{dataset_tag}_manifest_clips_downsampled.csv"
    if not best_model_dir.exists():
        raise FileNotFoundError(f"Best model dir not found: {best_model_dir}")
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")

    infer_batch_size = 8
    infer_num_workers = 6
    infer_prefetch_factor = 2
    infer_decode_thread_type = "AUTO"

    evaluate_test_split(
        model_dir=best_model_dir,
        manifest_path=manifest_path,
        num_frames=16,
        sampling_rate=4,
        batch_size=infer_batch_size,
        num_workers=infer_num_workers,
        prefetch_factor=infer_prefetch_factor,
        decode_thread_type=infer_decode_thread_type,
        export_dir=metrics_export_dir,
    )
