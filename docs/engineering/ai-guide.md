# AI 开发指南（AI Guide）

✅ 来源：`ai/`。架构总览见 [architecture/ai-architecture.md](../architecture/ai-architecture.md)。

## 环境

- Python（README：3.14 / CUDA 13.2；Docker 镜像用 3.12-slim）+ FFmpeg。
- 安装：`pip install --extra-index-url https://download.pytorch.org/whl/cu130 -r requirements.txt`。
- ✅ 务必设置 `PYTHONPATH=src`（脚本依赖 `ai_app` 包从 `src/` 解析）。

## 三条链路与入口

| 链路 | 入口脚本 | 说明 |
| --- | --- | --- |
| 推理服务（请求式） | `scripts/serve.py` | 启动 FastAPI（uvicorn :8001），端点 `/health` `/predict/path` `/predict/upload` |
| 单次推理（CLI） | `scripts/infer.py` | 命令行推理 |
| 训练 | `scripts/train.py` | VideoMAE 微调（HF Trainer + EarlyStopping） |
| 实时流告警 | `scripts/stream_live_alert_service.py` | 消费直播流 + 持续性规则 + Pushover/SMS |
| 数据准备 | `scripts/build_manifest.py` 等 | 构建清单、切片、下采样、解压 |

## 运行推理服务

```bash
cd ai
export PYTHONPATH=src                  # Windows: $env:PYTHONPATH="src"
python scripts/serve.py                # 默认 0.0.0.0:8001
```

✅ 模型目录解析（`serving/deps.py`）：环境变量 `AI_MODEL_DIR`（默认 `outputs/videomae_baseline/best_model`），可调 `AI_DEVICE`/`AI_NUM_FRAMES`(16)/`AI_SAMPLING_RATE`(4)。

> ⚠️ **模型权重不在仓库**。无 `best_model` 时服务启动加载会抛 `FileNotFoundError`。

## 实时流告警的关键参数

✅ `run_stream_service(...)` 的可调项（默认值）：窗口 `window_sec=5`/步长 `step_sec=2`；`clip_positive_threshold=0.60`；持续性 `persistence_window_sec=60`、`persistence_hit_ratio=0.50`、`clear_hit_ratio=0.40`、`min_history_sec=30`、`min_hits=8`；黑屏门控阈值；通知冷却 `notification_cooldown_sec=120`；可选 SMS。`target_label` 默认 `"assault"`。

详细状态机见 [ai-architecture.md](../architecture/ai-architecture.md#5-实时流告警链路关键且为实验性)。

## 重要边界

- ✅ AI **不连数据库、不调后端**：实时告警仅产出 Pushover/SMS + 本地 CSV（`stream_timeline.csv`、`stream_alarm_events.csv`）。不写 `detection_events`。
- ❓ `configs/*.yaml`（train/eval/infer）当前为空——超参当前在脚本内；训练数据集与标签体系未在仓库文档化。
- ✅ 训练完成会发 Pushover 通知。

## 与告警相关的工具

- `src/ai_app/utils/pushover.py` — Pushover 推送（单条/批量）。
- `src/ai_app/utils/sms.py` — SMS 批量发送。
- 这两者是 AI 子系统目前**唯一的对外副作用通道**。
