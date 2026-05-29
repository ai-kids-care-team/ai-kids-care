# 可观测性（Observability）

> 如实记录**当前**可观测能力。整体处于早期阶段。

## 日志

| 组件 | 现状 | 证据 |
| --- | --- | --- |
| 后端 | ✅ `logging.level.root: DEBUG`（committed）；标准 Spring Boot 控制台日志。⚠️ DEBUG 级日志量大，可能含敏感信息 | `application.yml` |
| 前端 | 🔶 浏览器端 Axios；无集中日志 | — |
| AI 推理服务 | 🔶 uvicorn/FastAPI 默认日志 | `serve.py` |
| AI 实时告警 | ✅ 控制台打印 + **CSV 落盘**（`stream_timeline.csv` 逐窗口、`stream_alarm_events.csv` 告警事件） | `stream_live_alert_service.py` |

🔶 无集中式日志聚合（无 ELK/Loki 等配置）。容器日志靠 `docker compose logs` 查看。

## 健康检查

| 组件 | 健康检查 |
| --- | --- |
| PostgreSQL | ✅ compose healthcheck（`pg_isready`，5s 间隔，120s start_period） |
| AI 服务 | ✅ `GET /health`（返回 model_dir/device/labels 等） |
| 后端 | ❓ 无专用 health 端点（未启用 Spring Actuator）；间接用 Swagger UI 可达性判断 |
| Neo4j | 🔶 compose 仅 `service_started`，无应用级健康检查 |
| 前端 | 🔶 Nginx 静态服务，无显式健康端点 |

## 指标 / 监控

- ❓ **无应用指标体系**：未见 Actuator/Micrometer/Prometheus 配置。
- ✅ 业务侧有部分"自带指标"字段：`detection_sessions.avg_latency_ms`、`inference_fps`（但当前由种子填充，非实时采集）。
- ✅ AI 实时告警的 CSV 可视为离线分析数据源（命中率、告警时长等）。

## 告警

- ✅ **业务告警**走 Pushover/SMS（AI 实时检测触发）。
- ❓ **系统告警**（服务宕机、错误率）无配置。

## 审计

- ✅ 有 `audit_logs` 表与对应 API。
- ❓ 各写操作是否实际写审计未确认（未见统一切面）。见 [security-architecture](../architecture/security-architecture.md#7-审计)。

## 待确认（汇总于 open-questions）

> ❓ 生产可观测性目标（日志聚合、指标、追踪、系统告警、SLO）尚无定义。当前能力面向开发/演示。
