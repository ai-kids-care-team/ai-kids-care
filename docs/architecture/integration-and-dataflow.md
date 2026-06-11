# 集成与数据流（Integration & Data Flow）

本文档描述组件间的**集成方式**与若干**典型时序流**，帮助理解请求/数据如何穿过系统。

## 1. 集成矩阵

✅ 来源：各组件配置与代码（见 [system-overview](system-overview.md#4-组件协作要点)）。

| 源 → 目标 | 协议/机制 | 同步性 | 证据 |
| --- | --- | --- | --- |
| 浏览器 → 前端 | HTTP（静态资源） | — | `nginx.conf` |
| 前端 → 后端 | HTTP REST（`/api/` 经 Nginx 反代；开发直连 :8080） | 同步 | `apiClient.ts`、`nginx.conf` |
| 后端 → PostgreSQL | JDBC / JPA | 同步 | `application.yml` |
| 后端 → Neo4j | Bolt（Neo4j Java Driver，原生 Cypher） | 同步 | `GraphRepository.java` |
| 后端 → Pushover | HTTP（pushover-client） | 同步 | `PushoverService` |
| data-loader → Neo4j | 主要读取提交到仓库的 CSV；仅 users 另有 psycopg 导入脚本 | 批处理（一次性） | `db/ne4j_kindergartens/` |
| AI 服务 ← 调用方 | HTTP REST（FastAPI :8001） | 同步 | `serving/app.py` |
| AI 实时告警 → Pushover/SMS | HTTP | 异步（流式触发） | `stream_live_alert_service.py` |

> ✅ 关键：**AI 服务与后端之间没有直接集成**。两者都对外暴露 HTTP，但代码中不互相调用，也不共享数据库连接。

## 2. 典型流：登录

✅ 来源 `AuthController`/`AuthService`、`apiClient.ts`。

```text
前端 →(POST /api/v1/auth/login {identifier, password})→ 后端
   后端: 按 loginId/email/phone 查 user → BCrypt 校验密码
        → 生成 accessToken & refreshToken（同法生成）
        → 取最近 ACTIVE 角色分配 → 返回 TokenVO{accessToken, refreshToken, role, id, loginId, expiresIn}
前端: 存 token 到 Redux + localStorage
```

## 3. 典型流：带鉴权的数据请求（前端预期）

✅ 来源 `apiClient.ts`。注意此为**前端实现的预期流程**；后端当前不强制校验（[security-architecture](security-architecture.md)）。

```text
前端发起请求
   请求拦截器: 注入 Authorization: Bearer <token>
后端响应
   ├─ 200 → 正常返回
   └─ 401 → 响应拦截器:
        取 localStorage.refreshToken → POST /api/v1/auth/refresh
        ├─ 成功 → 存新 token → 用新 token 重放原请求
        └─ 失败 → 清 token + 弹出登录框(openLoginModal)
```

## 4. 典型流：Phase 1A 已关闭的通用写入口

✅ 来源 `ChildrenController`、`DeviceTokenController`、`EventEvidenceFileController`、`CameraStreamController`、`SensitiveWriteContractTest`。体现当前控制器映射与 Phase 1A 止血后的 as-built 行为（见 [backend-architecture](backend-architecture.md)）。

```text
GET /api/v1/children?keyword=&page=&size=
   Controller(分页参数) → Service.listChildren → Repository(分页查询)
                        → MapStruct: Entity → ChildVO → 返回 Page<ChildVO>

POST /api/v1/children {ChildCreateDTO}
   当前通用 create 已关闭
    → Controller 不再提供 POST 映射
    → Spring MVC 返回 405 Method Not Allowed
    → 不调用 ChildrenService.createChildren
    → 替代性的专用安全 command 尚未实现

POST /api/v1/event_evidence_files {no public CreateDTO}
PUT /api/v1/event_evidence_files/{id} {no public UpdateDTO}
   当前公共 generic write 已关闭
    → Controller 不再提供 POST / PUT 映射
    → Spring MVC 在现有 GET / DELETE path 上返回 405 Method Not Allowed
    → 不调用 EventEvidenceFileService.createEventEvidenceFile / updateEventEvidenceFile
    → `storageUri` 保留在 entity 内部存储模型，不再属于公共 write contract

POST /api/v1/device_tokens {no public CreateDTO}
PUT /api/v1/device_tokens/{id} {no public UpdateDTO}
   当前公共 generic write 已关闭
     → Controller 不再提供 POST / PUT 映射
     → Spring MVC 在现有 GET / DELETE path 上返回 405 Method Not Allowed
     → 不调用 DeviceTokenService.createDeviceToken / updateDeviceToken
     → `pushToken` 仍保留在 entity 内部存储模型，但未来只能通过绑定服务端身份的专用 command 接收

POST /api/v1/camera_streams {no public CreateDTO}
PUT /api/v1/camera_streams/{id} {no public UpdateDTO}
   当前公共 generic write 已关闭
    → Controller 不再提供 POST / PUT 映射
    → Spring MVC 在现有 GET / DELETE path 上返回 405 Method Not Allowed
    → 不调用 CameraStreamService.createCameraStream / updateCameraStream
    → `sourceUrl`、`streamUser` 与 `stream_password_*` 仍保留在 entity 内部存储模型，公共读取 contract 只发布 `hasPassword`、`sourceProtocol`、`playbackUrl`、`playbackProtocol` 等非敏感字段
```

> 同一轮 Phase 1A 止血也适用于 `POST /api/v1/users`、`/children`、`/guardians`、`/teachers`，以及 `POST`/`PUT` `/api/v1/device_tokens`、`/event_evidence_files`、`/camera_streams`：通用敏感写入口已关闭并返回 `405`。这只是停止公共 generic write 暴露面；公开注册审批流、server-side session、tenant context 和授权隔离尚未在当前实现中落地。

## 5. 典型流：以儿童为中心的关系图

✅ 来源 `GraphController` → `GraphService` → `GraphRepository`（Cypher）。

```text
GET /api/v1/graph/children/{childId}
   → 后端在 Neo4j 执行 Cypher:
       MATCH (ch:Child {child_id})
       OPTIONAL MATCH (c:Class)-[:HAS_CHILD]->(ch)
       OPTIONAL MATCH (t:Teacher)-[:HAS_CLASS]->(c)
       OPTIONAL MATCH (k:Kindergarten)-[:HAS_TEACHER]->(t)
       OPTIONAL MATCH (ch)-[rg:HAS_GUARDIAN]->(g:Guardian)
   → 组装 ChildGraphVO{child, classInfo, teacher, kindergarten, guardians[](按 priority 排序)}
   → 前端用 reagraph 可视化
```

> 前提：Neo4j 已被 data-loader 加载。当前大部分数据来自 CSV 快照，不保证与 PostgreSQL 同步。

## 6. 典型流：AI 推理（请求式）

✅ 来源 `serving/app.py` + `predictor.py`。

```text
调用方 →(POST :8001/predict/upload  multipart 视频)→ AI 服务
   写临时文件 → PyAV 解码 → 抽 16 帧 → VideoMAEImageProcessor → 模型前向 → softmax
   → 返回 {predicted_label, confidence, scores[top_k], model_dir, device} → 删临时文件
```

## 7. 典型流：AI 实时告警（流式，实验性）

✅ 来源 `stream_live_alert_service.py`。详细状态机见 [ai-architecture](ai-architecture.md#5-实时流告警链路关键且为实验性)。

```text
FLV 流 → 滑动窗口推理 → 黑屏门控 → 持续性规则(去抖) → alarm_on
   → Pushover + (可选)SMS + CSV
   ✗ 不写 detection_events（与后端无数据集成）
```

## 8. 数据流断点（汇总）

> ❓ 系统中存在两处明显的"数据流断点"，理解它们对评估系统完整性至关重要：
>
> 1. **AI 检测 ↮ 业务后端**：实时检测结果不落库，后端事件数据靠种子。完整闭环未连通。
> 2. **PostgreSQL → Neo4j 一次性**：图数据不随 PG 增量更新，需重新运行 loader。
>
> 详见 [modernization/open-questions.md](../modernization/open-questions.md)。
