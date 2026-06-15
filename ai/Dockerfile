FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src \
    AI_MODEL_DIR=/app/outputs/videomae_baseline/best_model \
    AI_SERVICE_HOST=0.0.0.0 \
    AI_SERVICE_PORT=8001

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        libgl1 \
        libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./

RUN python -m pip install --upgrade pip \
    && pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cu130 -r requirements.txt

COPY src ./src
COPY scripts ./scripts
COPY outputs ./outputs
COPY .env.example ./

EXPOSE 8001

CMD ["python", "scripts/serve.py"]
