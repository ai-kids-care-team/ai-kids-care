from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path
from tempfile import NamedTemporaryFile

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile

from ai_app.inference.predictor import PredictionResult, VideoPredictor
from ai_app.serving.deps import get_predictor
from ai_app.serving.schemas import (
    HealthResponse,
    PredictPathRequest,
    PredictResponse,
    PredictionScoreResponse,
)


def _to_response(
        result: PredictionResult,
        predictor: VideoPredictor,
        video_path: str | None,
) -> PredictResponse:
    return PredictResponse(
        predicted_id=result.predicted_id,
        predicted_label=result.predicted_label,
        confidence=result.confidence,
        scores=[
            PredictionScoreResponse(label=score.label, probability=score.probability)
            for score in result.scores
        ],
        model_dir=str(predictor.model_dir),
        device=predictor.device,
        video_path=video_path,
    )


def _predict_or_raise(
        predictor: VideoPredictor,
        video_path: str | Path,
        top_k: int,
        num_frames: int | None,
        sampling_rate: int | None,
) -> PredictionResult:
    try:
        return predictor.predict_video(
            video_path=video_path,
            top_k=top_k,
            num_frames=num_frames,
            sampling_rate=sampling_rate,
        )
    except (FileNotFoundError, ValueError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@asynccontextmanager
async def lifespan(_: FastAPI):
    get_predictor()
    yield


app = FastAPI(
    title="AI Kids Care Inference Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health", response_model=HealthResponse)
def health(predictor: VideoPredictor = Depends(get_predictor)) -> HealthResponse:
    return HealthResponse(
        status="ok",
        model_dir=str(predictor.model_dir),
        device=predictor.device,
        num_frames=predictor.num_frames,
        sampling_rate=predictor.sampling_rate,
        labels=predictor.labels,
    )


@app.post("/predict/path", response_model=PredictResponse)
def predict_from_path(
        request: PredictPathRequest,
        predictor: VideoPredictor = Depends(get_predictor),
) -> PredictResponse:
    result = _predict_or_raise(
        predictor=predictor,
        video_path=request.video_path,
        top_k=request.top_k,
        num_frames=request.num_frames,
        sampling_rate=request.sampling_rate,
    )
    return _to_response(result, predictor, request.video_path)


@app.post("/predict/upload", response_model=PredictResponse)
async def predict_from_upload(
        file: UploadFile = File(...),
        top_k: int = Form(3),
        num_frames: int | None = Form(None),
        sampling_rate: int | None = Form(None),
        predictor: VideoPredictor = Depends(get_predictor),
) -> PredictResponse:
    suffix = Path(file.filename or "").suffix or ".mp4"
    temp_path: Path | None = None

    try:
        with NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_path = Path(temp_file.name)
            while True:
                chunk = await file.read(1024 * 1024)
                if not chunk:
                    break
                temp_file.write(chunk)

        result = _predict_or_raise(
            predictor=predictor,
            video_path=temp_path,
            top_k=top_k,
            num_frames=num_frames,
            sampling_rate=sampling_rate,
        )
        return _to_response(result, predictor, file.filename)
    finally:
        await file.close()
        if temp_path is not None and temp_path.exists():
            os.remove(temp_path)
