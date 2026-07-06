## Why

C2/C3 门禁复核暴露的两条 AI 侧 follow-up：

- **INT-obs（C2 integration 复核，low）**：AI 侧 `MAX_WORKERS`（`supervisor.py` 读 env）**无上限 clamp**。C2 给后端 claim `capacity` 加了 `@Max(64)`；若运维误配 `MAX_WORKERS > 64`，该 deployment 会陷入「claim→后端 400→保留旧 worker→下轮重试」的**静默停滞循环**（不 crash，但永不认领新流）。
- **SEC-C3-01（C3 security 复核，medium）**：C3 的 SEC-04 流式大小校验只压了 `/predict/upload` 的 **RAM 峰值**——FastAPI/Starlette 在依赖（认证 + 大小校验）运行**之前**就 `await request.form()`，把整个 multipart body spool 到磁盘（>~1MiB 溢写临时盘）。故未认证/超大 body 仍能在被 413 前耗尽带宽 + 临时盘。真闭合需在 **body 解析前**按 `Content-Length` 早退。

## What Changes

- **INT-obs（ai）**：`supervisor.py` 读 `MAX_WORKERS` 后 **clamp 到 ≤64**（与后端 `@Max(64)` 对齐），超限时 `logging.warning` 提示「已截断到 64，与后端 claim 上界一致」。避免误配导致静默停滞。
- **SEC-C3-01（ai）**：给 FastAPI app 加一个**轻量 ASGI 中间件 / 依赖**，在**读取 body 前**校验 `Content-Length`：超过 `AI_MAX_UPLOAD_MB` 直接 413，不进入 form 解析（不 spool 整 body）。保留 C3 已有的分块流式校验作为无 `Content-Length`（chunked transfer）时的兜底。
  - 说明：AI :8001 当前 compose `expose:`（非公网），本项是**纵深防御**，让分离 GPU 主机/跨网 VPN 部署后该端点自带 body 上限，不依赖边缘 Caddy 配置（Caddy 现只 front 主应用，不 front :8001）。

## Non-goals

- 不改 SEC-03 认证 / SEC-04 分块校验逻辑本身（本项在其之前加 Content-Length 早退层）。
- 不改推理算法/模型/supervisor 的 claim 循环语义（只 clamp capacity 输入值）。
- 不启用/新增 compose service，不动部署拓扑。
- 不做边缘 Caddy 配置（:8001 不在 Caddy 后；若未来经 Caddy 暴露可另加 `request_body max_size`，属部署面）。
