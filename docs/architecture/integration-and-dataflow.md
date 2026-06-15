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
   后端: 按 loginId/email/phone 查 ACTIVE user → BCrypt 校验密码
        → 要求恰好存在一条 ACTIVE 角色分配（缺失或多条均拒绝，不回退 GUARDIAN）
        → 生成 accessToken & refreshToken（同法生成）
        → 取最近 ACTIVE 角色分配 → 返回 TokenVO{accessToken, refreshToken, role, id, loginId, expiresIn}
前端: token 只存 Redux 内存；不写 user/access/refresh token 到 localStorage
```

### 2.1 公开注册申请（Phase 1B）

```text
前端 → POST /api/v1/auth/guardian-child-verifications {childRrnFirst6, childRrnBack7}
        → 200 {verified=true|false}，不返回儿童 PII
     → POST /api/v1/auth/register
   ├─ PLATFORM_IT_ADMIN → 400，首次 persistence 前拒绝
   └─ GUARDIAN / TEACHER / KINDERGARTEN_ADMIN / SUPERADMIN
        → users.status = PENDING
        → user_role_assignments.status = PENDING
        → profile.status = PENDING
        → 园级角色 membership.status = PENDING
        → 201 {userId, status=PENDING, createdAt}
前端: 显示“申请已提交、待审批”，不自动登录、不写 token/localStorage
```

> 返回完整 `ChildVO` 的通用 `GET /api/v1/children/rrn` 已关闭。Guardian 注册 payload 不携带 `childId`；服务端在注册事务中再次按完整 RRN 匹配儿童，并从该记录派生 kindergarten scope 与 membership。`KINDERGARTEN_ADMIN` 仅接受 `DIRECTOR` / `VICE_DIRECTOR` level，普通 `TEACHER` 不能提交这两个管理员 level。
>
> 审批 endpoint 和激活事务尚未实现。`child_guardian_relationships` 当前没有 status 列，Guardian 申请仍会创建关系行，但 PENDING guardian、membership 与 role assignment 会阻止其成为有效授权关系；该模型缺口留给后续审批/资源关系阶段处理。

## 3. 典型流：带鉴权的数据请求（前端预期）

✅ 来源 `apiClient.ts`。注意此为 server-side session 上线前的临时流程；后端当前仍未统一强制校验（[security-architecture](security-architecture.md)）。

```text
前端发起请求
   请求拦截器: 从 Redux 内存注入 Authorization: Bearer <token>
后端响应
   ├─ 200 → 正常返回
   └─ 401 → 清空 Redux 会话 + 弹出登录框(openLoginModal)

刷新浏览器
   → 内存会话丢失
   → 重新登录；前端当前不自动调用 refresh
```

## 4. 典型流：Phase 1A 已关闭的敏感通用入口

✅ 来源 `ChildrenController`、`DeviceTokenController`、`EventEvidenceFileController`、`CameraStreamController`、`SensitiveWriteContractTest`。体现当前控制器映射与 Phase 1A 止血后的 as-built 行为（见 [backend-architecture](backend-architecture.md)）。

```text
GET|POST|PUT|DELETE /api/v1/{users|children|guardians|teachers}
   当前 Controller 不发布任何 operation
    → 匿名调用不能枚举账户、儿童、Guardian 或 Teacher profile
    → entity/service/mapper 内部读取模型保留，等待 self/admin/relationship policy 后由专用 contract 使用

GET|POST|PUT|DELETE /api/v1/{event_evidence_files|device_tokens}
   当前 Controller 不发布任何 operation
    → 不公开 evidence existence/retention/hash 或 device registry metadata
    → `storageUri` / `pushToken` 仍保留在内部存储模型，未来只能由受控 query/command 使用

GET|POST|PUT|DELETE /api/v1/detection_events
   当前 Controller 不发布任何 operation
    → 客户端提交 kindergartenId 不再获得整园事件 feed
    → 等待 authenticated tenant/resource policy 后再开放

POST /api/v1/camera_streams {no public CreateDTO}
PUT|DELETE /api/v1/camera_streams/{id}
   当前公共 generic write/delete 已关闭
    → Controller 仅保留 GET list/detail
    → 不调用 CameraStreamService 的 create/update/delete 链
    → `sourceUrl`、`streamUser`、`playbackUrl` 与 `stream_password_*` 仍保留在 entity 内部存储模型
    → 公共读取 contract 只发布不含可播放地址或凭据的配置元数据
```

> 同一轮止血已关闭 User、Child、Guardian、Teacher、DetectionEvent、DeviceToken、EventEvidenceFile 的全部公共 operation；CctvCamera、DetectionSession、CameraStream 仅保留脱敏 GET，通用写删链关闭；EventReview、NotificationRule、Superadmin、AppreciationLetter 同样不发布公共 operation。Phase 1B 已进一步把公开注册收敛为 PENDING 申请，但审批流、server-side session、tenant context 和授权隔离尚未落地。

## 5. 典型流：以儿童为中心的关系图（公共入口已关闭）

`GraphRepository` 和 Neo4j Cypher 映射仍保留为内部实现，但 `GraphController` 当前不发布 `/api/v1/graph/children/{childId}`。原因是响应包含儿童、Guardian、Teacher 与关系顺位等 S1 数据，而资源授权尚未实现。前端关系图入口只显示“待权限实现”提示，不再发起公共请求。

> Neo4j 当前大部分数据仍来自 CSV 快照，不保证与 PostgreSQL 同步。未来重新开放关系图必须同时完成资源关系授权、最小字段投影和 S1 访问审计。

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
