#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 20:15
@Author  : zhangjunfan1997@naver.com
@File    : predictor
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import torch
from transformers import VideoMAEForVideoClassification, VideoMAEImageProcessor

from ai_app.inference.pipeline import decode_video_pyav, sample_frame_indices


@dataclass(frozen=True)
class PredictionScore:
    label: str
    probability: float


@dataclass(frozen=True)
class PredictionResult:
    predicted_id: int
    predicted_label: str
    confidence: float
    scores: list[PredictionScore]


class VideoPredictor:
    def __init__(
            self,
            model_dir: str | Path,
            device: str | None = None,
            num_frames: int = 16,
            sampling_rate: int = 4,
    ) -> None:
        self.model_dir = Path(model_dir)
        if not self.model_dir.exists():
            raise FileNotFoundError(f"Model dir not found: {self.model_dir}")

        self.device = self._resolve_device(device)
        self.num_frames = int(num_frames)
        self.sampling_rate = int(sampling_rate)

        self.processor = VideoMAEImageProcessor.from_pretrained(self.model_dir)
        self.model = VideoMAEForVideoClassification.from_pretrained(self.model_dir)
        self.model.to(self.device)
        self.model.eval()

    @staticmethod
    def _resolve_device(device: str | None) -> str:
        if device:
            return device
        return "cuda" if torch.cuda.is_available() else "cpu"

    def _label_for_id(self, label_id: int) -> str:
        id2label = self.model.config.id2label
        if str(label_id) in id2label:
            return str(id2label[str(label_id)])
        return str(id2label[label_id])

    @property
    def labels(self) -> list[str]:
        num_labels = int(self.model.config.num_labels)
        return [self._label_for_id(label_id) for label_id in range(num_labels)]

    def predict_video(
            self,
            video_path: str | Path,
            top_k: int = 3,
            num_frames: int | None = None,
            sampling_rate: int | None = None,
    ) -> PredictionResult:
        video_path = Path(video_path)
        if not video_path.exists():
            raise FileNotFoundError(f"Video not found: {video_path}")

        effective_num_frames = int(num_frames or self.num_frames)
        effective_sampling_rate = int(sampling_rate or self.sampling_rate)

        frames = decode_video_pyav(video_path)
        indices = sample_frame_indices(
            total_frames=len(frames),
            num_frames=effective_num_frames,
            sampling_rate=effective_sampling_rate,
        )
        sampled_frames = [frames[i] for i in indices]

        inputs = self.processor(sampled_frames, return_tensors="pt")
        pixel_values = inputs["pixel_values"].to(self.device)

        with torch.no_grad():
            outputs = self.model(pixel_values=pixel_values)
            probs = torch.softmax(outputs.logits, dim=-1)[0].cpu().tolist()

        ranked_ids = sorted(range(len(probs)), key=lambda label_id: probs[label_id], reverse=True)
        scored = [
            PredictionScore(
                label=self._label_for_id(label_id),
                probability=float(probs[label_id]),
            )
            for label_id in ranked_ids[:max(1, int(top_k))]
        ]

        predicted_id = ranked_ids[0]
        best = scored[0]

        return PredictionResult(
            predicted_id=predicted_id,
            predicted_label=best.label,
            confidence=best.probability,
            scores=scored,
        )
