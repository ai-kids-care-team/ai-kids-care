---
name: analyze-integration
description: 从集成/边界角度审查组件——跨组件契约一致性（REST API↔前端 hook、AI↔后端 ingest、后端↔DB schema、SSE/事件协议、配置拓扑）。integration-analyst 使用。当需要边界一致性检查、契约比对、接口错位/未接线排查、端到端链路审查时使用。
---

# analyze-integration — 集成与边界角度审查方法

多组件系统里**最高价值、最易藏 bug** 的角度。核心不是「确认某物存在」，而是**把边界两侧同时读出来，逐字段比对 shape**。按 finding schema 输出（id 前缀 `INT-`），每条**必引两侧** `file:line`。

## 为何这样审（原则）
契约错位（字段名、可空性、枚举值不一致）通常**编译不报错、运行时静默失败**——数据悄悄丢、去重悄悄失效、事件悄悄不触发。单侧阅读看不出来，必须双侧交叉比对。这正是 QA 式边界检查的精髓。

## 边界清单（按本工程栈）

### 1. REST：后端 ↔ 前端
- 对每个用到的端点，**同时**读后端 controller + DTO 与前端 `services/apis/*.api.ts` 的 request/response 类型。
- 逐字段比对：字段名（驼峰 vs 下划线）、可空性、枚举值集合、嵌套对象 shape、分页/包装结构。
- 找：后端返回 X 前端读 Y、前端发的字段后端不收、枚举多/少值。

### 2. AI ↔ 后端 ingest（重点）
- `ai/src/ai_app/utils/backend_ingest.py` 产出的 payload ↔ 后端 `POST /api/v1/internal/detection-events` 的接收 DTO。
- 比对：字段命名一致性、`dedup_key` 生成规则与字段名、Bearer 鉴权头、event_type 枚举映射（**12 AI 标签 → 13 enum** 是否全覆盖、OTHER 兜底是否正确）。

### 3. 后端 ↔ DB schema
- JPA 实体/JPQL 字段 ↔ `db/initdb` schema ↔ Flyway 迁移：列名/类型/约束一致？
- **枚举三处同步**：event_type_enum / notification_status_enum 等在 DB、后端 enum、前端常量三处是否一致（retire-dictionary-tables 后枚举改走代码/前端静态，更要查）。

### 4. 实时协议：SSE / 事件
- SSE：后端端点（heartbeat 间隔 25s、Last-Event-ID 重连、replay 上限 200）↔ 前端 EventSource 监听与重连逻辑是否对齐。
- Spring 事件：detection 持久化 → notification 触发链是否闭合（AFTER_COMMIT hook、关系图、dedupe）。

### 5. 租户 / 会话上下文
- 前端是否（错误地）在客户端存 role/tenant；与后端「会话内 activeKindergartenId」契约是否一致（前端不应自行切租户）。

### 6. 配置 / 拓扑契约
- `docker-compose*.yml` 服务名/端口/healthcheck ↔ 各组件连接串/env 期望。
- CI 门禁（.github/workflows）是否覆盖每个组件（backend 测试 / 前端 lint-build / ai pytest / compose 校验）。

## 手法（你是 general-purpose，可执行）
- 成对打开文件、Grep 字段名在两侧的出现、对比 `@JsonProperty` 与前端 interface。
- 一侧缺失（如某 api 文件不存在）→ 这本身是 finding（疑似未接线），标 medium。
- 必要时写小脚本提取两侧字段集合做 diff。

> ⚠️ **gitignore 陷阱（务必）**：本工程 `frontend/` 整树在 repo `.gitignore` 内，Claude 的 **Grep / Glob 工具默认静默跳过被忽略的文件**。直接用它们搜前端会得到「0 命中 / 文件不存在」的**假象**，把「后端就绪、前端未接线」误判成「前端确实没有」——而前端其实存在、只是被忽略了。凡涉前端（或任何 .gitignore 内目录）的存在性/未接线判断，**必须用裸 `rg`/`ripgrep`（经 Bash）或 `rg --no-ignore` 绕过忽略规则**确证后再下结论。「一侧缺失」类 finding 不绕过验证不得出具。

## 协作
你是边界问题汇聚点——接收其余三位转来的可疑边界点并验证。边界处鉴权缺口 → `security-analyst`；错位的结构根因 → `architecture-analyst`。完成写 `_workspace/integration_findings.md` 并通知 lead（附错位 top-3）。
