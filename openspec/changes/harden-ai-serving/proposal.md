## Why

2026-07-06 分析在 AI 子栈命中三项硬化（`ai/` 域，非破坏性）：

- **SEC-03（MEDIUM）**：FastAPI 推理端点 `:8001` 的 `/predict/upload` 与 `/health` **完全未认证**（`app.py:79,91` 仅 `Depends(get_predictor)`）。当前 `ai/docker-compose.yml` 用 `expose:`（非 `ports:`）把端口限在 compose 网内中和了风险，但维护者的真流部署决策是**分离 GPU 主机 + 跨网 VPN**——一旦 GPU 主机独立，未认证推理端点的风险回来（任意网内主体可提交推理/探活）。补 Bearer 认证是**始终正确的纵深防御**。
- **SEC-04（MEDIUM）**：`predict_from_upload` 先 `content = await file.read()`（`app.py:105`）**把整个上传读入内存**，大小校验在 `:107` 之后 → 超大上传在被拒前已耗尽内存（DoS，叠加 SEC-03 可被未认证方触发）。
- **ARC-02（MEDIUM）**：`ai_app/supervisor.py:284-295` 用 `importlib` 按文件路径加载**包外** `scripts/stream_live_alert_service.py`（607 行），包→入口脚本依赖倒挂。该 importlib 是有意的 lazy ML import（避免 supervisor import 时拉起 ML 重依赖），但路径耦合脆弱。

## What Changes

- **SEC-03 加 Bearer 认证**：`/predict/upload`（写/推理面）加 Bearer token 依赖，token 经 `${ENV}` 注入 + fail-fast（`@NotBlank` 语义，缺失即启动失败，遵守 invariant #5）。`/health` 保持可无认证探活（或仅在配置了 token 时校验，实现者按最小惊讶取舍并说明）。token 与 `AI_SERVICE_TOKEN`（AI→backend 方向）**语义不同**，用独立 env（如 `AI_INFERENCE_TOKEN`），避免跨方向复用。
- **SEC-04 流式大小校验**：改为**读取时增量校验**（分块读，累计超 `AI_MAX_UPLOAD_MB` 立即中止并 413），或先查 `Content-Length` 早退——**在把全量读入内存前**拒绝超大上传。保留既有扩展名白名单 + magic-byte 校验层。
- **ARC-02 消 importlib 倒挂（保入口不破）**：把 `stream_live_alert_service.py` 的 `run_stream_service` 逻辑迁入**包内模块**（如 `ai_app/live/alert_service.py`）；`supervisor.py` 改**包内 lazy import**（函数内 `from ai_app.live.alert_service import run_stream_service`，仍延迟到子进程/调用时，保住「不在 supervisor import 时拉 ML」）；`scripts/stream_live_alert_service.py` 保留为**薄 shim**（`from ai_app.live.alert_service import *` / `run_stream_service` + `__main__` 转发），**入口路径、compose CMD、外部引用一律不破**。

## Non-goals

- **不改部署启用面**：不新增/启用 compose live worker service（属部署变更，须维护者批准）——仅改代码结构与端点认证，`ai/docker-compose.yml` 的 `expose:` 拓扑不动。
- 不删 `pushover.py`/`sms.py`（仍被训练脚本引用）。
- 不做对象存储 evidence（属 INT-02/C7，决策门 D-STORE）。
- 不改推理算法/模型加载/VideoMAE 管线。
