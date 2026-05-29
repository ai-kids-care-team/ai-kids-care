# AI 架构（AI Architecture）

✅ 主要来源：`ai/src/ai_app/`、`ai/scripts/`、`ai/requirements.txt`、`ai/Dockerfile`、`ai/docker-compose.yml`、`ai/README.md`。

## 1. 概述

AI 模块基于 **VideoMAE**（HuggingFace Transformers 的视频分类模型）做**视频异常行为分类**。它包含三条相对独立的链路：

1. **训练（offline）** — 微调 VideoMAE 得到模型权重。
2. **推理服务（online, request/response）** — FastAPI，对单段视频做分类。
3. **实时流告警（online, streaming）** — 消费直播流，连续推理 + 去抖动判定 + 外部告警。

✅ 关键依赖（`requirements.txt`）：`transformers==5.4.0`、`torch==2.11.0+cu130`、`av==17.0.0`（PyAV/FFmpeg 解码）、`fastapi`、`uvicorn`、`scikit-learn`、`accelerate`、`pandas`。环境：Python 3.14 / CUDA 13.2（README）；Docker 镜像用 `python:3.12-slim`。

## 2. 代码结构

```text
ai/
├── src/ai_app/
│   ├── datasets/      # loader.py(VideoClipManifestDataset) / collator / preprocess
│   ├── models/        # factory.py / loader.py（模型构建与加载）
│   ├── inference/     # predictor.py(VideoPredictor) / pipeline.py(解码与抽帧)
│   ├── serving/       # app.py(FastAPI) / deps.py(依赖注入) / schemas.py(Pydantic)
│   ├── training/      # trainer.py / callbacks.py / metrics.py
│   └── utils/         # io / logger / seed / pushover / sms
├── scripts/           # 可执行入口（见下）
├── configs/           # train.yaml / eval.yaml / infer.yaml（❓ 当前为空占位）
├── outputs/           # 模型产物（videomae_baseline/best_model）
└── examples/, data/
```

## 3. 推理服务（FastAPI）

✅ 入口 `scripts/serve.py` → `uvicorn ai_app.serving.app:app`，`host=0.0.0.0 port=8001`。

✅ 端点（`serving/app.py`）：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/health` | 返回 model_dir、device、num_frames、sampling_rate、labels |
| `POST` | `/predict/path` | 用**服务器可访问的视频路径**预测 |
| `POST` | `/predict/upload` | 上传视频文件预测（临时文件，用后即删） |

✅ 推理核心 `inference/predictor.py` 的 `VideoPredictor`：

- 默认 **16 帧、采样率 4**（可被请求覆盖）。
- 用 `pipeline.decode_video_pyav` 解码 + `sample_frame_indices` 抽帧 → `VideoMAEImageProcessor` 预处理 → 模型前向 → `softmax` → 取 top-k。
- ✅ 设备自动选择：有 CUDA 用 `cuda`，否则 `cpu`。
- ✅ 模型目录解析（`serving/deps.py`）：环境变量 `AI_MODEL_DIR`，默认 `outputs/videomae_baseline/best_model`；`AI_DEVICE`/`AI_NUM_FRAMES`/`AI_SAMPLING_RATE` 可调。
- ✅ `get_predictor()` 用 `@lru_cache` 实现**单例**，FastAPI `lifespan` 启动时预热加载。

> ✅ 模型权重不在仓库（`outputs/.../best_model` 需实际存在才能推理）。Docker 通过卷 `./outputs:/app/outputs:ro` 挂载。

## 4. 训练管线（offline）

✅ 入口 `scripts/train.py`：

- 用 HuggingFace `Trainer` + `TrainingArguments` 微调 `VideoMAEForVideoClassification`。
- 数据来自**清单（manifest）驱动**的 `VideoClipManifestDataset`（`datasets/loader.py`），即按 CSV/清单列出视频片段与标签。
- `EarlyStoppingCallback` + 自定义 `MemoryCleanupCallback`（显存清理）。
- 指标：`accuracy` 与 `macro_f1`（`compute_metrics`）。
- `set_seed(42)` 保证可复现。
- 训练完成通过 **Pushover** 通知。

✅ 配套数据准备脚本（`scripts/`）：`build_manifest.py`（构建清单）、`downsample_manifest_videos.py`（下采样）、`extract_binary_event_clips.py`（切片）、`unzip_and_delete.py`。

> ❓ `configs/train.yaml` 等当前为空文件——训练超参当前**硬编码在脚本中**还是另有来源？训练数据集来源/标签体系未在仓库内说明。

## 5. 实时流告警链路（关键，且为"实验性"）

✅ 入口 `scripts/stream_live_alert_service.py`（`run_stream_service`），文档串注释自述："consume one FLV stream URL → window-based VideoMAE inference → apply persistence rule → send pushover alert"。

工作流：

```text
FLV/RTSP 直播流 (PyAV 打开)
   │  按 window_sec=5s 取窗口，step_sec=2s 滑动
   ▼
黑屏门控(black-screen gate)：均值亮度/标准差过低则判窗口无效（摄像头遮挡/断流）
   │
   ▼
VideoMAE 推理 → target_label("assault") 的概率 target_prob
   │
   ▼
持续性规则(Persistence Rule)——去抖动状态机：
   - clip_hit = (target_prob ≥ clip_positive_threshold=0.60)
   - 在 persistence_window_sec=60s 滑窗内统计命中率
   - 命中率 ≥ 0.50 且 命中数 ≥ 8 且 历史跨度 ≥ 30s → alarm_on
   - 命中率 ≤ clear_hit_ratio=0.40（或窗口失效）→ alarm_off
   │
   ▼
告警分发：alarm_on 时发送
   - ✅ Pushover 推送
   - ✅ SMS 批量（可选，enable_sms_batch_notification）
   - notification_cooldown_sec=120s 冷却防轰炸
   │
   ▼
本地 CSV 记录：stream_timeline.csv（逐窗口）+ stream_alarm_events.csv（告警事件）
```

✅ 还具备断流自动重连（`reconnect_wait_sec`）、运行时长/窗口数上限、帧下采样（`max_short_side=360`）等工程化参数。
✅ 共享逻辑来自 `realtime_persistence_demo.py`（抽帧、标签映射、FPS 解析等）。

### 重要边界（必读）

> ✅ **AI 子系统与业务后端解耦**：`ai/` 全目录**没有**连接 PostgreSQL、也没有调用后端 `/api/v1` 的代码（已全量检索，仅有 `utils/pushover.py`、`utils/sms.py`、训练用 `requests`）。
> 
> 含义：实时告警当前只产出 **Pushover/SMS + 本地 CSV**，**不写入** `detection_sessions`/`detection_events`。因此：
> - 后端 API 中看到的检测数据来自**种子数据**，非线上 AI。
> - "AI 检测 → 落库 → 后端事件复核 → 前端展示 → 通知引擎"的完整闭环**尚未打通**。
> 
> ❓ 这是产品完整性的核心待确认项，见 [open-questions](../modernization/open-questions.md) 与 [integration-and-dataflow](integration-and-dataflow.md)。

## 6. 部署

✅ `ai/docker-compose.yml`：独立服务 `ai-inference`，端口 `8001`，`outputs` 只读挂载。**不在**根 `docker-compose.yml` 中——AI 服务需单独启动。
