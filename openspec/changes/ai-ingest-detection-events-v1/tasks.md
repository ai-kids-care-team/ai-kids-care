## 1. 决策前置（apply 前，维护者裁定）

- [x] 1.1 维护者裁定流枚举方案（design D3）：**已定 A=后端「列流」内部端点**（2026-06-30）——第 4 节后端工作转为必做
- [ ] 1.2 维护者确认本 AI 部署的租户/流范围与 `MAX_WORKERS` 上限（Open Questions 2、3）
- [ ] 1.3 维护者批准部署面变更（改 `ai/docker-compose.yml` 及生产叠加层新增常驻 worker/supervisor service）——破坏性/部署，逐项批准

## 2. AI 端：正确性修正（纯代码，先行，单测护航）

- [ ] 2.1 在 `stream_live_alert_service.py` 的 `alarm_on` 跃迁瞬间捕获墙钟告警起始时刻（`datetime.now(timezone.utc)`），保存于状态
- [ ] 2.2 用捕获的告警起始秒喂 `backend_ingest.build_dedup_key(stream_id, onset)`（替换当前的提交时刻 `time.time()`）
- [ ] 2.3 用真实告警窗口填 `submit_event` 的 `startTime`（告警起始）/`endTime`（当前窗末端），替换 `now_iso`/`now_iso`
- [ ] 2.4 扩展 `ai/tests/test_backend_ingest.py`（或 stream service 单测）：同一告警/去抖产生同 `dedupKey`；event 时间窗非零时长；injectable mock，无 torch/av/真实流
- [ ] 2.5 docker `python:3.12` 跑 `PYTHONPATH=src python -m pytest ai/tests -v` 全绿

## 3. AI 端：supervisor + 多摄像头编排

- [ ] 3.1 新增 supervisor（`ai/scripts/` 或 `ai/src/ai_app/`）：读活跃流集合（来源依 1.1 选型）→ 每路流拉起一个子进程跑 `run_stream_service(...)`
- [ ] 3.2 worker 守护：子进程退出按退避重启；任一 worker 异常不终止 supervisor（对齐 spec「Crashed worker is restarted」）
- [ ] 3.3 流集合 reconcile：新增流起 worker、移除流停 worker，不重启未受影响的 worker；尊重 `MAX_WORKERS` 上限
- [ ] 3.4 每路 worker 沿用既有 `STREAM_ID → /streams/{id}/credentials → URL`、`create_session → submit_event`、`AI_SERVICE_TOKEN`；保持 AI 不直连 DB、租户由后端推导
- [ ] 3.5 supervisor 单测（injectable mock：流枚举、拉起/重启/增删、上限保护），无真实子进程/流依赖
- [ ] 3.6 `ai/.env.example`：单流配置 → 多流配置（依 1.1 选型补 `STREAM_IDS`/列流端点开关、`MAX_WORKERS`）

## 4. 后端（方案 A 已选 → 必做）

- [ ] 4.1 新增内部只读端点 `GET /api/v1/internal/streams`（`@Hidden`、`hasRole("AI_SERVICE")`），返回本部署应消费的活跃流 `[{streamId, modelId, ...}]`，不含解密凭据
- [ ] 4.2 端点查询写入 JPQL/SQL 的 `kindergarten_id`/活跃谓词（服务端租户与可见集把控，禁加载后过滤）
- [ ] 4.3 后端测试：`ROLE_AI_SERVICE` 可读、其它角色/无 token 401/403；只返回活跃流；无 schema 迁移
- [ ] 4.4 若需后端 `./gradlew test`：本机走 DooD（挂仓库根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`）

## 5. 部署（维护者批准后落地）

- [ ] 5.1 `ai/docker-compose.yml` 新增 worker/supervisor service（与 :8001 `serve.py` 并存，独立可伸缩/下线）
- [ ] 5.2 生产 compose 叠加层（`.prod.yml`/`.cd.yml`）相应配置 worker/supervisor（含 `JAVA_BACKEND_URL`、`AI_SERVICE_TOKEN`、`MAX_WORKERS`、网络可达）
- [ ] 5.3 `compose-config` CI 通过；确认根 `docker-compose.yml` 仍不含 AI 栈（ai 子栈独立）

## 6. 文档 / spec 收口

- [ ] 6.1 应用本 change 的 `ai-detection` spec 增量（MODIFIED「Current alert output (interim state)」+ ADDED worker/supervisor、dedup/时间窗 requirement）
- [ ] 6.2 更新 `CLAUDE.md` interim 段：由「AI 仍直发 Pushover/SMS demo、未接入 ingest、detection_events 全来自 seed」改为「ingest 链路已实现、worker 已部署运行、实时 event 由后端写入」
- [ ] 6.3 在 proposal/design 中记录 `pushover.py`/`sms.py` 保留理由（仍被训练脚本引用，非死代码，禁误删）

## 7. 验证与收尾

- [ ] 7.1 集成验证（条件允许）：后端 docker 起 + 手动起 supervisor（或最小 worker）对测试流跑一轮，确认后端写出真实 `detection_events`、SSE 推送、复核→家长通知链路可达
- [ ] 7.2 `openspec validate ai-ingest-detection-events-v1 --strict` 通过
- [ ] 7.3 记录 Open Questions 中未解项（GPU 容量、重连二级去重、多类 target_label、凭据轮转）为后续 follow-up
