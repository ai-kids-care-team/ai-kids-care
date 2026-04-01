from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from ai_app.inference.predictor import VideoPredictor


PROJECT_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_MODEL_DIR = PROJECT_ROOT / "outputs" / "videomae_baseline" / "best_model"


@dataclass(frozen=True)
class ServiceSettings:
    model_dir: Path
    device: str | None
    num_frames: int
    sampling_rate: int


def _resolve_model_dir(raw_value: str | None) -> Path:
    if not raw_value:
        return DEFAULT_MODEL_DIR

    candidate = Path(raw_value)
    if candidate.is_absolute():
        return candidate

    return (PROJECT_ROOT / candidate).resolve()


@lru_cache
def get_settings() -> ServiceSettings:
    return ServiceSettings(
        model_dir=_resolve_model_dir(os.getenv("AI_MODEL_DIR")),
        device=os.getenv("AI_DEVICE") or None,
        num_frames=int(os.getenv("AI_NUM_FRAMES", "16")),
        sampling_rate=int(os.getenv("AI_SAMPLING_RATE", "4")),
    )


@lru_cache
def get_predictor() -> VideoPredictor:
    settings = get_settings()
    return VideoPredictor(
        model_dir=settings.model_dir,
        device=settings.device,
        num_frames=settings.num_frames,
        sampling_rate=settings.sampling_rate,
    )
