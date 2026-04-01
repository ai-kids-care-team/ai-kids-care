from __future__ import annotations

from pydantic import BaseModel, Field


class PredictionScoreResponse(BaseModel):
    label: str
    probability: float


class PredictPathRequest(BaseModel):
    video_path: str = Field(..., description="Absolute or service-local video path")
    top_k: int = Field(default=3, ge=1, le=20)
    num_frames: int | None = Field(default=None, ge=1)
    sampling_rate: int | None = Field(default=None, ge=1)


class PredictResponse(BaseModel):
    predicted_id: int
    predicted_label: str
    confidence: float
    scores: list[PredictionScoreResponse]
    model_dir: str
    device: str
    video_path: str | None = None


class HealthResponse(BaseModel):
    status: str
    model_dir: str
    device: str
    num_frames: int
    sampling_rate: int
    labels: list[str]
