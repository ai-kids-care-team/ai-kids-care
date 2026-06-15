# AI 推理服务 API（FastAPI）

✅ 来源：`ai/src/ai_app/serving/app.py`、`schemas.py`、`scripts/serve.py`。架构见 [ai-architecture](../architecture/ai-architecture.md)。

## 基本信息

- 服务：`AI Kids Care Inference Service`（FastAPI，version 0.1.0）。
- 默认监听：`0.0.0.0:8001`（`AI_SERVICE_HOST`/`AI_SERVICE_PORT` 可调）。
- ⚠️ 独立部署，**不在**后端/根 compose 中；与后端无数据集成。
- 自带 OpenAPI：FastAPI 默认 `http://localhost:8001/docs`（🔶 Swagger UI）。

## 端点

### `GET /health`

返回模型与运行状态。

✅ 响应（`HealthResponse`）字段：`status`、`model_dir`、`device`、`num_frames`、`sampling_rate`、`labels`（模型可识别的标签列表）。

### `POST /predict/path`

用**服务器可访问的视频路径**预测。

✅ 请求（`PredictPathRequest`）：
```json
{
  "video_path": "/path/to/video.mp4",
  "top_k": 3,
  "num_frames": 16,        // 可选，默认取服务配置
  "sampling_rate": 4        // 可选
}
```

### `POST /predict/upload`

上传视频文件预测（`multipart/form-data`）。

✅ 表单字段：`file`（视频，必填）、`top_k`（默认 3）、`num_frames`（可选）、`sampling_rate`（可选）。
- 服务端写临时文件 → 推理 → **用后即删**。

### 预测响应（`PredictResponse`，path/upload 通用）

✅ 字段：
- `predicted_id`、`predicted_label`、`confidence`
- `scores`：`[{label, probability}]`（top_k）
- `model_dir`、`device`、`video_path`

## 错误

✅ 文件不存在或解码/参数错误 → `400`（`FileNotFoundError`/`ValueError` 被转为 `HTTPException 400`）。

## 注意

- ⚠️ 推理需 `outputs/videomae_baseline/best_model`（模型权重不在仓库，见 [ai-guide](../engineering/ai-guide.md)）。
- ✅ 模型在服务启动（FastAPI `lifespan`）时预热加载，`@lru_cache` 单例复用。
- ❓ 实时流告警（`stream_live_alert_service.py`）**不是** HTTP API，而是独立运行的脚本（Pushover/SMS + CSV 输出），见 [ai-architecture](../architecture/ai-architecture.md#5-实时流告警链路关键且为实验性)。
