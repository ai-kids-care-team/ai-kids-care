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

import random
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import accuracy_score
from transformers import (
    VideoMAEForVideoClassification,
    VideoMAEImageProcessor,
    Trainer,
    TrainingArguments,
)

from src.ai_app.datasets.loader import (
    VideoClipManifestDataset,
    build_label_mappings,
    videomae_collate_fn,
)


def set_seed(seed: int = 42) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=1)
    acc = accuracy_score(labels, preds)
    return {"accuracy": acc}


def main():
    set_seed(42)

    project_root = Path(__file__).resolve().parent.parent
    manifest_path = project_root / "data" / "processed" / "manifest_clips_binary.csv"
    output_dir = project_root / "outputs" / "videomae_baseline"

    checkpoint = "MCG-NJU/videomae-base-finetuned-kinetics"

    num_frames = 16
    sampling_rate = 4

    processor = VideoMAEImageProcessor.from_pretrained(checkpoint)
    label2id, id2label = build_label_mappings(manifest_path)

    print("label2id:", label2id)
    print("id2label:", id2label)

    train_dataset = VideoClipManifestDataset(
        manifest_path=manifest_path,
        processor=processor,
        label2id=label2id,
        split="train",
        num_frames=num_frames,
        sampling_rate=sampling_rate,
        train_random_sampling=True,
    )

    eval_dataset = VideoClipManifestDataset(
        manifest_path=manifest_path,
        processor=processor,
        label2id=label2id,
        split="val",
        num_frames=num_frames,
        sampling_rate=sampling_rate,
        train_random_sampling=False,
    )

    print(f"train dataset size: {len(train_dataset)}")
    print(f"val dataset size: {len(eval_dataset)}")

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
        save_total_limit=2,
        load_best_model_at_end=True,
        metric_for_best_model="accuracy",
        greater_is_better=True,
        per_device_train_batch_size=2,
        per_device_eval_batch_size=2,
        gradient_accumulation_steps=1,
        num_train_epochs=3,
        learning_rate=5e-5,
        weight_decay=0.05,
        warmup_ratio=0.1,
        fp16=torch.cuda.is_available(),
        dataloader_num_workers=0,
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
