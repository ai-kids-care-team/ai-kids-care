---
globs: ai/**
disclosure: path-scoped
---

# AI 子栈实现约定（`ai/`）

FastAPI 推理服务（端口 8001）+ VideoMAE（HuggingFace Transformers）+ PyTorch + PyAV；独立 compose 栈，包管理 uv。

## 长生命周期 supervisor

AI 子栈以**长生命周期 supervisor**（`ai/scripts/stream_supervisor.py`，多摄像头 process-per-stream）运行检测→ingest 循环，与 :8001 FastAPI 推理端点**并存**。supervisor 经只读 `GET /api/v1/internal/streams`（`ROLE_AI_SERVICE`）枚举活跃流并 reconcile；实时 `detection_events` 由**后端从 AI 推理写入**（非 seed）。

注：worker/supervisor 的 compose service（`ai/docker-compose.yml` 的 `ai-live-supervisor`）**已定义入库**，但生产实际启用仍属部署面变更，须经维护者批准。

## ingest 客户端行为

调 `POST /api/v1/internal/detection-{sessions,events}`（契约细节见 `contracts.md`）时，AI 侧 **bounded retry 3 次 / 指数退避 / 10s 超时**。

## 命令

```bash
# AI：CI 仅装精简依赖（无 torch）
cd ai && PYTHONPATH=src python -m pytest tests/ -v
```
