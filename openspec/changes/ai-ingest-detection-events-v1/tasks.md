## 1. 决策前置（apply 前，维护者裁定）

- [x] 1.1 维护者裁定流枚举方案（design D3）：**已定 A=后端「列流」内部端点**（2026-06-30）——第 4 节后端工作转为必做
- [ ] 1.2 维护者确认本 AI 部署的租户/流范围与 `MAX_WORKERS` 上限（Open Questions 2、3）
- [ ] 1.3 维护者批准部署面变更（改 `ai/docker-compose.yml` 及生产叠加层新增常驻 worker/supervisor service）——破坏性/部署，逐项批准

## 2. AI 端：正确性修正（纯代码，先行，单测护航）

- [x] 2.1 在 `stream_live_alert_service.py` 的 `alarm_on` 跃迁瞬间捕获墙钟告警起始时刻（`transition_wall - history_span`），保存于 `PersistenceState.alarm_onset_wall`
- [x] 2.2 用捕获的告警起始秒喂 `backend_ingest.build_dedup_key(stream_id, onset)`（替换当前的提交时刻 `time.time()`）——经 `ai_app.utils.alarm_event.build_alarm_event_params`
- [x] 2.3 用真实告警窗口填 `submit_event` 的 `startTime`（告警起始）/`endTime`（跃迁/当前窗末端），替换 `now_iso`/`now_iso`
- [x] 2.4 新增 `ai/tests/test_alarm_event_params.py`：同一告警/去抖产生同 `dedupKey`（秒精度 jitter-stable）；event 时间窗非零时长；纯逻辑无 torch/av/真实流
- [x] 2.5 docker `python:3.12` 跑 `PYTHONPATH=src python -m pytest tests/ -v` 全绿（115 passed）

## 3. AI 端：supervisor + 多摄像头编排

- [x] 3.1 新增 `ai/src/ai_app/supervisor.py`（核心，无 ML 依赖）+ `ai/scripts/stream_supervisor.py`（入口）：经 `GET /api/v1/internal/streams` 读活跃流 → 每路流拉起一个 spawn 子进程跑 `run_stream_service(...)`
- [x] 3.2 worker 守护：子进程退出按退避重启（`restart_not_before` 门）；任一 worker 异常/枚举失败不终止 supervisor（best-effort、keep current）
- [x] 3.3 流集合 reconcile：新增流起 worker、移除流停 worker（terminate），不重启未受影响的 worker；尊重 `MAX_WORKERS` 上限
- [x] 3.4 每路 worker 沿用既有 `streamId → /streams/{id}/credentials → URL`、`create_session → submit_event`、`AI_SERVICE_TOKEN`；AI 不直连 DB、租户由后端推导
- [x] 3.5 supervisor 单测 `ai/tests/test_stream_supervisor.py` + `test_stream_registry.py`（injectable mock：枚举、拉起/重启/增删、上限、枚举失败 keep），无真实子进程/流
- [x] 3.6 `ai/.env.example`：补 `MAX_WORKERS` / `STREAM_POLL_INTERVAL_SEC`（registry 端点驱动，无需 per-stream `STREAM_ID`；保留单流 fallback）

## 4. 后端（方案 A 已选 → 必做）

- [x] 4.1 新增内部只读端点 `GET /api/v1/internal/streams`（加到 `@Hidden` 的 `StreamCredentialController`，`hasRole("AI_SERVICE")` 既有规则覆盖），返回 `[{streamId, modelId, kindergartenId}]`，不含解密凭据（`ActiveStreamVO`）
- [x] 4.2 端点查询 `CameraStreamRepository#findActiveStreamsForAi`：`enabled = true` 活跃谓词 + `kindergartenId` 在 JPQL 内从 `cctvCameras.kindergarten.id` 投影（禁加载后过滤）；modelId 由 service 取活跃 `ai_models`
- [x] 4.3 后端测试 `InternalStreamListApiTest`：`ROLE_AI_SERVICE` 可读、无/错 token 4xx；只返回 `enabled` 流、disabled 排除；响应无凭据字段；无 schema 迁移
- [x] 4.4 后端 `./gradlew test`：Lead 走 DooD 串行运行——**全套件绿（BUILD SUCCESSFUL）**，`InternalStreamListApiTest` 通过、零回归

## 5. 部署（维护者批准后落地）

- [x] 5.1 `ai/docker-compose.yml` 新增 `ai-live-supervisor` service（与 :8001 `serve.py` 并存，`command` override，独立伸缩/下线）—— **BREAKING/部署，已隔离并标注，待维护者批准**
- [~] 5.2 生产配置：AI 子栈**独立**（根 `.prod.yml`/`.cd.yml` 不含 AI 栈，无单独 ai prod overlay）——故 worker/supervisor 配置（`JAVA_BACKEND_URL`/`AI_SERVICE_TOKEN`/`MODEL_ID`/`MAX_WORKERS`/`STREAM_POLL_INTERVAL_SEC`、网络可达）以 `${ENV}` 注入直接落在 `ai/docker-compose.yml`；如需独立 ai prod overlay 由维护者裁定
- [x] 5.3 `docker compose -f ai/docker-compose.yml config -q` 通过；确认根 `docker-compose.yml` 仍不含 AI 栈（`ai-inference`/`ai-live-supervisor` 仅在 `ai/docker-compose.yml`）

## 6. 文档 / spec 收口

- [x] 6.1 `ai-detection` spec 增量已编写（MODIFIED「Current alert output (interim state)」+ ADDED worker/supervisor、dedup/时间窗 requirement）；`openspec validate --strict` 通过——应用到主 spec 由 archive 步骤完成（Lead）
- [x] 6.2 更新 `CLAUDE.md` interim 段：改为「ingest 链路两侧已实现、supervisor 运行检测→ingest 循环、实时 `detection_events` 由后端写入」（标注 compose worker 须维护者批准）
- [x] 6.3 proposal/design 已记录 `pushover.py`/`sms.py` 保留理由（仍被 `train.py`/`extract_binary_event_clips.py` 引用，非死代码，禁误删）

## 7. 验证与收尾

- [ ] 7.1 端到端集成验证：本机无 GPU/真实 FLV 流/Java —— **未运行**；留维护者在有流环境验证后端写出真实 `detection_events` + SSE + 复核→家长通知（AI 单测 + 后端契约测试已护航各单元）
- [x] 7.2 `openspec validate ai-ingest-detection-events-v1 --strict` 通过
- [x] 7.3 Open Questions 未解项（GPU 容量、重连二级去重、多类 `target_label`、凭据轮转、租户/流分片、per-stream modelId 映射）已在 design Open Questions 留作 follow-up
