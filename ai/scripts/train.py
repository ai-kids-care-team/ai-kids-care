#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 20:13
@Author  : zhangjunfan1997@naver.com
@File    : train
"""
# !/usr/bin/env python
# -*- coding: utf-8 -*-

from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.metrics import accuracy_score, f1_score
from transformers import (
    EarlyStoppingCallback,
    VideoMAEForVideoClassification,
    VideoMAEImageProcessor,
    Trainer,
    TrainingArguments,
)

from ai_app.datasets.loader import (
    VideoClipManifestDataset,
    build_label_mappings,
    videomae_collate_fn,
)
from ai_app.training.callbacks import MemoryCleanupCallback
from ai_app.utils.pushover import send_pushover_notification


def set_seed(seed: int = 42) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=1)
    acc = accuracy_score(labels, preds)
    macro_f1 = f1_score(labels, preds, average="macro")
    return {"accuracy": acc, "macro_f1": macro_f1}


def filter_manifest_by_file_size(
        manifest_path: Path,
        output_dir: Path,
        min_video_size_bytes: int = 1024,
) -> Path:
    """
    Drop missing/tiny video files before training starts.
    """
    df = pd.read_csv(manifest_path)

    valid_mask = []
    dropped_paths = []
    for raw_path in df["video_path"].astype(str).tolist():
        path = Path(raw_path)
        try:
            is_valid = path.is_file() and path.stat().st_size >= min_video_size_bytes
        except OSError:
            is_valid = False

        valid_mask.append(is_valid)
        if not is_valid:
            dropped_paths.append(raw_path)

    valid_df = df[np.array(valid_mask, dtype=bool)].reset_index(drop=True)
    dropped_count = len(df) - len(valid_df)
    if dropped_count == 0:
        return manifest_path

    output_dir.mkdir(parents=True, exist_ok=True)
    filtered_manifest_path = output_dir / "manifest_clips_all_filtered.csv"
    valid_df.to_csv(filtered_manifest_path, index=False, encoding="utf-8")

    print(
        f"[WARN] Dropped {dropped_count} missing/tiny clips "
        f"(size < {min_video_size_bytes} bytes) before training."
    )
    for path in dropped_paths[:10]:
        print(f"  - {path}")
    if len(dropped_paths) > 10:
        print("  - ...")
    print(f"[INFO] Using filtered manifest: {filtered_manifest_path}")

    return filtered_manifest_path


def main():
    set_seed(42)
    dataset_tag = "01_assault"

    project_root = Path(__file__).resolve().parent.parent
    manifest_path = project_root / "data" / "processed" / f"{dataset_tag}_manifest_clips_downsampled.csv"
    output_dir = project_root / "outputs" / f"{dataset_tag}_videomae_baseline"

    checkpoint = "MCG-NJU/videomae-base-finetuned-kinetics"

    num_frames = 16
    sampling_rate = 4
    min_video_size_bytes = 1024
    gc_collect_interval = 200
    gc_every_n_steps = 200
    early_stopping_patience = 6
    early_stopping_threshold = 2e-3
    per_device_train_batch_size = 2
    per_device_eval_batch_size = 2
    gradient_accumulation_steps = 4
    warmup_ratio_of_one_epoch = 0.05
    dataloader_num_workers = 8
    dataloader_persistent_workers = dataloader_num_workers > 0
    dataloader_prefetch_factor = 4 if dataloader_num_workers > 0 else None

    manifest_path = filter_manifest_by_file_size(
        manifest_path=manifest_path,
        output_dir=output_dir,
        min_video_size_bytes=min_video_size_bytes,
    )

    processor = VideoMAEImageProcessor.from_pretrained(checkpoint)
    label2id, id2label = build_label_mappings(manifest_path)

    print("label2id:", label2id)
    print("id2label:", id2label)

    common_dataset_kwargs = {
        "manifest_path": manifest_path,
        "processor": processor,
        "label2id": label2id,
        "num_frames": num_frames,
        "sampling_rate": sampling_rate,
        "decode_thread_type": None,
        "skip_decode_errors": True,
        "max_decode_retries": 16,
        "min_video_size_bytes": min_video_size_bytes,
    }

    train_dataset = VideoClipManifestDataset(
        **common_dataset_kwargs,
        split="train",
        train_random_sampling=True,
        gc_collect_interval=gc_collect_interval,
    )

    eval_dataset = VideoClipManifestDataset(
        **common_dataset_kwargs,
        split="val",
        train_random_sampling=False,
        gc_collect_interval=gc_collect_interval,
    )

    print(f"train dataset size: {len(train_dataset)}")
    print(f"val dataset size: {len(eval_dataset)}")

    steps_per_epoch = max(
        1,
        math.ceil(len(train_dataset) / (per_device_train_batch_size * gradient_accumulation_steps)),
    )
    warmup_steps = max(1, int(round(steps_per_epoch * warmup_ratio_of_one_epoch)))
    print(
        f"steps_per_epoch: {steps_per_epoch}, "
        f"warmup_steps(5% of one epoch): {warmup_steps}"
    )

    model = VideoMAEForVideoClassification.from_pretrained(
        checkpoint,
        label2id=label2id,
        id2label=id2label,
        ignore_mismatched_sizes=True,
    )

    training_args = TrainingArguments(
        output_dir=str(output_dir),
        remove_unused_columns=False,
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_strategy="steps",
        logging_steps=10,
        save_total_limit=20,
        load_best_model_at_end=True,
        metric_for_best_model="eval_accuracy",
        greater_is_better=True,
        per_device_train_batch_size=per_device_train_batch_size,
        per_device_eval_batch_size=per_device_eval_batch_size,
        gradient_accumulation_steps=gradient_accumulation_steps,
        num_train_epochs=1000,
        learning_rate=1e-5,
        weight_decay=0.05,
        max_grad_norm=1.0,
        warmup_steps=warmup_steps,
        fp16=torch.cuda.is_available(),
        dataloader_num_workers=dataloader_num_workers,
        dataloader_pin_memory=True,
        dataloader_persistent_workers=dataloader_persistent_workers,
        dataloader_prefetch_factor=dataloader_prefetch_factor,
        restore_callback_states_from_checkpoint=False,
        report_to="none",
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        processing_class=processor,
        data_collator=videomae_collate_fn,
        compute_metrics=compute_metrics,
        callbacks=[
            MemoryCleanupCallback(gc_every_n_steps=gc_every_n_steps),
            EarlyStoppingCallback(
                early_stopping_patience=early_stopping_patience,
                early_stopping_threshold=early_stopping_threshold,
            ),
        ],
    )

    train_result = trainer.train()
    print(train_result)

    eval_result = trainer.evaluate()
    print("eval_result:", eval_result)

    trainer.save_model(str(output_dir / "best_model"))
    processor.save_pretrained(str(output_dir / "best_model"))

    print(f"Saved model to: {output_dir / 'best_model'}")


if __name__ == "__main__":
    main()
    # Push Notification to myself
    send_pushover_notification("Training finished!", "Wake Up!!!")
