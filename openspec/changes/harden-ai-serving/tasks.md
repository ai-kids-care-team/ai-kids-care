# Tasks — harden-ai-serving (C3)

> 纯 `ai/` 域，非破坏性（不启用/新增 compose service，不删训练脚本引用的模块）。TDD（pytest，CI 无 torch）。

## 1. SEC-03 — Bearer 认证
- [ ] 1.1 加 Bearer 依赖（如 `ai_app/serving/auth.py`）：从 `${AI_INFERENCE_TOKEN}` 读，缺失/空即 fail-fast（启动或首次校验时明确报错，不静默放行）
- [ ] 1.2 `/predict/upload` 挂认证依赖；缺/错 token → 401
- [ ] 1.3 `/health`：实现者取舍（保持开放探活 vs 配置后校验），在 proposal/notes 说明选型
- [ ] 1.4 token 值绝不入日志；用独立 env `AI_INFERENCE_TOKEN`（勿复用 `AI_SERVICE_TOKEN`）
- [ ] 1.5 `.env.example` 加占位 `AI_INFERENCE_TOKEN=`（仅占位，不提交真值）
- [ ] 1.6 测试：无 token→401、错 token→401、对 token→通过（用小 fake predictor 或 mock，勿依赖 torch）

## 2. SEC-04 — 流式大小校验
- [ ] 2.1 改 `/predict/upload` 在**全量读入内存前**限制大小：分块累计读、超 `AI_MAX_UPLOAD_MB` 立即 413；或先查 `Content-Length` 早退
- [ ] 2.2 保留扩展名白名单 + magic-byte 校验（顺序合理：大小早退 → 扩展名 → magic）
- [ ] 2.3 测试：超限上传在读满前被 413 拒（断言不整体缓冲）；正常小文件仍通过校验链

## 3. ARC-02 — 消 importlib 倒挂
- [ ] 3.1 新建 `ai_app/live/alert_service.py`（迁入 `run_stream_service` 及其依赖），保持 API 签名
- [ ] 3.2 `supervisor.py::_load_run_stream_service` 改包内 lazy import（函数内 `from ai_app.live.alert_service import run_stream_service`），删 importlib file-path 逻辑
- [ ] 3.3 `scripts/stream_live_alert_service.py` 改薄 shim：re-export `run_stream_service` + `__main__` 转发；入口/compose CMD/外部引用不破
- [ ] 3.4 测试：`import ai_app.supervisor` 不触发 ML 重依赖（lazy 仍成立）；shim 仍可 `python scripts/stream_live_alert_service.py` 语义等价（导入层面）；supervisor 加载路径不再依赖文件路径

## 4. 门禁
- [ ] 4.1 `cd ai && PYTHONPATH=src python -m pytest tests/ -v` 全绿（CI 精简依赖，无 torch）
- [ ] 4.2 `docker compose config` 不破（compose 拓扑未改，若碰 env 需在 `.env.example` 同步）
