from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path
from tempfile import NamedTemporaryFile

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from starlette.concurrency import run_in_threadpool

from ai_app.inference.predictor import PredictionResult, VideoPredictor
from ai_app.serving.auth import get_inference_token, require_bearer_token
from ai_app.serving.deps import get_predictor
from ai_app.serving.schemas import (
    HealthResponse,
    PredictResponse,
    PredictionScoreResponse,
)

_ALLOWED_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".webm"}
_MAGIC_BYTES_LEN = 12
# SEC-04: bound how much memory a single read() call can consume so an over-limit
# upload is rejected well before the whole payload is buffered.
_UPLOAD_CHUNK_BYTES = 1024 * 1024  # 1 MiB

_VIDEO_MAGIC_CHECKS = [
    (0, b"RIFF"),                # AVI
    (4, b"ftyp"),                # MP4 / MOV
    (0, b"\x1a\x45\xdf\xa3"),   # MKV / WebM (EBML)
]


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
    # SEC-03: fail fast at process startup if AI_INFERENCE_TOKEN is missing/blank,
    # rather than silently starting an unauthenticated inference endpoint.
    get_inference_token()
    yield


app = FastAPI(
    title="AI Kids Care Inference Service",
    version="0.1.0",
    lifespan=lifespan,
)


# SEC-03 trade-off (harden-ai-serving, C3): /health intentionally stays unauthenticated.
# It is read-only liveness/readiness metadata (no PII, no inference execution) consumed by
# container orchestration/health probes that typically don't carry application secrets;
# requiring a bearer token here would complicate ops tooling for little security benefit,
# since the network is already scoped by compose `expose:` (not a public port). The
# write/inference-triggering endpoint below (`/predict/upload`) is the one that requires
# authentication.
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


async def _read_upload_within_limit(file: UploadFile, max_upload_mb: int) -> bytes:
    """Read ``file`` incrementally, rejecting an over-limit upload before it is fully buffered.

    SEC-04: the previous implementation called ``await file.read()`` (whole-file read) and
    only checked the size afterwards, so an oversized upload was fully pulled into memory
    before being rejected (DoS surface, compounded by SEC-03 if the caller is unauthenticated).
    This reads in bounded chunks and checks the running total after each one, aborting with
    413 as soon as the cumulative size crosses ``max_upload_mb`` — the tail of an oversized
    payload is never read.
    """
    max_bytes = max_upload_mb * 1024 * 1024
    buffer = bytearray()
    while True:
        chunk = await file.read(_UPLOAD_CHUNK_BYTES)
        if not chunk:
            break
        buffer.extend(chunk)
        if len(buffer) > max_bytes:
            await file.close()
            raise HTTPException(
                status_code=413,
                detail=f"File exceeds {max_upload_mb} MB limit",
            )
    return bytes(buffer)


@app.post(
    "/predict/upload",
    response_model=PredictResponse,
    dependencies=[Depends(require_bearer_token)],
)
async def predict_from_upload(
        file: UploadFile = File(...),
        # top_k: 1–50。上限保守取 50（模型标签数通常远小于此值；
        # 超出实际标签数时 predictor.py 内部 ranked_ids[:max(1,top_k)] 会自然截断，
        # 但仍需防止超大值导致 predictor 内循环异常消耗资源）。
        top_k: int = Form(default=3, ge=1, le=50),
        # num_frames: 1 帧以上有意义；上限取 256（防止极大值导致内存溢出）。
        num_frames: int | None = Form(default=None, ge=1, le=256),
        # sampling_rate: 1 以上有意义；上限取 128（典型视频 30fps，128 已足够稀疏）。
        sampling_rate: int | None = Form(default=None, ge=1, le=128),
        predictor: VideoPredictor = Depends(get_predictor),
) -> PredictResponse:
    # Layer 1: streamed file size limit (SEC-04) — see _read_upload_within_limit.
    max_upload_mb = int(os.getenv("AI_MAX_UPLOAD_MB", "512"))
    content = await _read_upload_within_limit(file, max_upload_mb)

    # Layer 2: extension whitelist
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in _ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=422,
            detail=f"Unsupported file type '{suffix}'. Allowed: {sorted(_ALLOWED_EXTENSIONS)}",
        )

    # Layer 3: magic-byte validation
    header = content[:_MAGIC_BYTES_LEN]
    if not any(header[offset:offset + len(sig)] == sig for offset, sig in _VIDEO_MAGIC_CHECKS):
        raise HTTPException(
            status_code=422,
            detail="File content does not match a supported video container",
        )

    temp_path: Path | None = None

    try:
        with NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_path = Path(temp_file.name)
            temp_file.write(content)

        result = await run_in_threadpool(
            _predict_or_raise,
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
