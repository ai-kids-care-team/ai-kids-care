# Tasks — ai-inference-hardening-followups (INT-obs + SEC-C3-01)

> 纯 `ai/` 域，非破坏性（不启用 compose service，不动部署拓扑）。TDD（pytest，无 torch）。

## 1. INT-obs — MAX_WORKERS clamp
- [ ] 1.1 `supervisor.py` 读 `MAX_WORKERS` 后 clamp 到 `min(value, 64)`；超限 `logging.warning`（提示与后端 @Max(64) 对齐）
- [ ] 1.2 测试：MAX_WORKERS=100 → 实际用 64 + warn 触发；MAX_WORKERS=2 → 不变、不 warn

## 2. SEC-C3-01 — Content-Length 早退
- [ ] 2.1 给 FastAPI app 加中间件/依赖：读取前若 `Content-Length` > `AI_MAX_UPLOAD_MB` → 413，不进入 `request.form()`/body 读取
- [ ] 2.2 保留 C3 分块流式校验作为无 Content-Length（chunked）时兜底
- [ ] 2.3 测试：超 Content-Length 的请求在进入路由 handler 前即 413（断言未触发 form 解析/未 spool）；正常小请求仍通过认证+校验链；无 Content-Length 时分块兜底仍生效
- [ ] 2.4 不破坏 SEC-03 认证顺序（认证与 body 上限的先后：Content-Length 早退可在认证前或后，实现者取舍并说明——建议 body 上限在最前，避免未认证大 body）

## 门禁
- [ ] G1 `cd ai && PYTHONPATH=src python -m pytest tests/ -v` 全绿
- [ ] G2 `docker compose config` 不破（若碰 env 在 .env.example 同步）
