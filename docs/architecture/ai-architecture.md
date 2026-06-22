# AI 架构（AI Architecture）

✅ 主要来源：`ai/src/ai_app/`、`ai/scripts/`、`ai/pyproject.toml`、`ai/Dockerfile`、`ai/docker-compose.yml`、`ai/README.md`。

## 1. 概述

AI 模块基于 **VideoMAE**（HuggingFace Transformers 的视频分类模型）做**视频异常行为分类**。它包含三条相对独立的链路：

1. **训练（offline）** — 微调 VideoMAE 得到模型权重。
2. **推理服务（online, request/response）** — FastAPI，对单段视频做分类。
3. **实时流告警（online, streaming）** — 消费直播流，连续推理 + 去抖动判定 + 外部告警。

✅ 关键依赖（`pyproject.toml`）：`transformers==5.4.0`、`torch>=2.11.0`（cu130 CUDA 变体，由 `[tool.uv.sources]` 解析）、`av==17.0.0`（PyAV/FFmpeg 解码）、`fastapi`、`uvicorn`、`scikit-learn`、`accelerate`、`pandas`。环境：Python 3.14 / CUDA 13.2（README）；Docker 镜像用 `python:3.12-slim`。

## 1.1 模型与数据来源（Provenance）

> ✅ 2026-06-07 由维护者提供，补 [OQ-AI-2](../modernization/open-questions.md)。**日后撰写 README「数据来源」章节时引用本节。**

- **基础检查点（base checkpoint）**：`MCG-NJU/videomae-base-finetuned-kinetics`（HuggingFace；VideoMAE base，已在 Kinetics-400 微调）。项目在其上**再微调**到异常行为分类。
- **训练数据与标签**：AI Hub「**이상행동 CCTV 영상**」(Abnormal Behavior CCTV Video) 数据集。
  - dataSetSn=**171**；规模 **717 小时 / 8,436 clips**；**12 类**异常行为。
  - URL：`https://aihub.or.kr/aihubdata/data/view.do?currMenu=115&topMenu=100&dataSetSn=171`（AI Hub 为韩国政府公开数据平台，需登录查看/下载）。
  - ⚠️ **使用条款/许可（2026-06-07 接手人提示）**：AI Hub 数据集通常需同意使用协议、限定用途；**作品集对外展示 + 处理此类儿童监控数据，落地前须核实并留痕数据使用许可范围**（商用 / 再分发 / 模型权重分发限制等）。
- **标签 → `event_type_enum` 映射**（与 DB 13 值近 1:1，`OTHER` 为 catch-all；佐证 [OQ-AI-3](../modernization/open-questions.md)）：

| AI Hub 类别 | `event_type_enum` | AI Hub 类别 | `event_type_enum` |
| --- | --- | --- | --- |
| 폭행 Assault | `ASSAULT` | 침입 Trespass | `TRESPASS` |
| 싸움 Fight | `FIGHT` | 투기 Dump | `DUMP` |
| 절도 Burglary | `BURGLARY` | 강도 Robbery | `ROBBERY` |
| 기물파손 Vandalism | `VANDALISM` | 데이트폭력/추행 Date violence | `DATEFIGHT` |
| 실신 Swoon | `SWOON` | 납치 Kidnap | `KIDNAP` |
| 배회 Wander | `WANDER` | 주취행동 Drunken | `DRUNKEN` |
| （无数据集对应，catch-all） | `OTHER` | | |

> 🔶 **实现待确认**：上表为"数据集类别 → 业务枚举"的概念映射；落地时还需确认微调模型 `id2label` 实际输出的 label **字符串**（实时脚本现用小写 `"assault"`），据此写代码级查表。归 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md) 实现项。

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

> 🔶 `configs/train.yaml` 等当前为空文件——训练超参当前**硬编码在脚本中**（应固化回 `configs`，属待办）。✅ 训练数据集来源与标签体系已记录于上文 **§1.1 模型与数据来源**（AI Hub「이상행동 CCTV 영상」, dataSetSn=171, 12 类）。

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
>
> ⚠️ **勘误（2026-06-07）**：此处的"解耦"是**当前临时演示态、非目标架构**——原始设计意图是 **AI 写库、后端从 DB 发通知**。目标方向（AI 直写核心表）见 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md)（V1）；AI 现存 Pushover/SMS 为收件人写死的演示代码，落地时由后端通知管线替换。详见 [ADR-0006 顶部勘误](../decisions/adr/ADR-0006-decoupled-ai-videomae.md)。

## 6. 部署

✅ `ai/docker-compose.yml`：独立服务 `ai-inference`，端口 `8001`，`outputs` 只读挂载。**不在**根 `docker-compose.yml` 中——AI 服务需单独启动。
