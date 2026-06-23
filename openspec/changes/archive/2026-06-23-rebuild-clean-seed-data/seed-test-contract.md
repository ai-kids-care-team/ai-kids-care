# 测试 ↔ seed 契约清单(重写红线)

> Task 1 交付物。来源:通读全部 19 个 `extends BaseIntegrationTest` 的集成测试(+ 它们引用的 `db/initdb` seed)。
> 重写业务 seed 时,以下 invariants **必须逐条保住**,否则测试整片红(FK all-or-nothing + 断言失败)。

## A. 必须保留的具体 seed 行/值(硬依赖,最危险)

| # | 约束 | 来源测试 |
|---|------|---------|
| A-1 | `users` 存在 `login_id='admin'`(`user_id=1`) | AuthEndpointTest(`availability_existingLoginId`) |
| A-2 | `children` 存在一行(seed 现为 `child_id=1`):`rrn_first6='200921'`,且完整 RRN(`'200921'+'4037926'`)的 **HMAC-SHA256(pepper=`'test-pepper-not-secret-2026'`)== 该行 `rrn_hash`** | AuthEndpointTest(`guardianChildVerification`,断言 `verified=true`)|

> ⚠️ A-2 是隐藏依赖:盲改这行的 RRN/hash 会让 `verified=true` 断言失败。重写若改动此 child,必须用同一 pepper 重算 `rrn_hash`。

## B. 园存在性 + 每园背景数量

| # | 约束 | 来源 |
|---|------|------|
| B-1 | `kindergarten_id=1` 存在且 `status='ACTIVE'` | AuthEndpoint / AdminApproval / PlatformAdminApproval / TeacherChild / GuardianChild / SecurityAudit / TeacherRoom / TeacherAssignment(园级角色 FK + `scopeId=1` 断言)|
| B-2 | `kindergarten_id=2` 存在且 ACTIVE(`OWN_KG=2`) | TenantIsolationIntegrationTest |
| B-3 | 至少 **2 个** kindergarten 存在(取 `WHERE id<>? LIMIT 1` 作外园) | EventReviewApiTest;建议保留 demo 原有的 **3 个园** |
| B-4 | 园1 `classes` **≥2** 行(`kindergartenClassCount()>1`) | TeacherAssignment(>1);AuthEndpoint(≥1)|
| B-5 | 园1 `rooms` **≥2** 行(`kindergartenRoomCount()>1` + 需要「未分配给测试 teacher 的负样本 room」) | TeacherRoomAssignment;AuthEndpoint(≥1)|
| B-6 | 园1 `detection_sessions` ≥1 行 | AuthEndpoint(`publishedVos`)|
| B-7 | `detection_events` ≥1 行(取 `ORDER BY event_id LIMIT 1`),其 `kindergarten_id` 指向存在的园 | EventReviewApiTest |
| B-8 | `ai_models` ≥1 行 | AuthEndpoint / DetectionIngest |
| B-9 | ≥1 条 `camera_streams` 行,其 `camera_id` 在 `room_camera_assignments` 有 **`end_at IS NULL`** 的 active 分配(可 JOIN 查到 active stream→camera→room 链) | DetectionIngestApiTest |
| B-10 | 园2(`OWN_KG=2`)下也要有可被 `findFirstEntityId` 查到的背景实体 → **apply 时核实 TenantIsolation 具体查哪些表 in 园2,镜像园1 的背景** | TenantIsolationIntegrationTest |

## C. seed 中**不得出现**的标识(测试自建 fixture,避唯一约束冲突)

**phone**(`uq_user_account_phone` 等):
`010-0000-9994`(TeacherRoom)、`010-0000-9995`(TeacherAssignment)、`010-0000-9996`(TenantIsolation)、`010-0000-7701`(DetectionIngest)、`010-0000-6601`~`6604`(EventReview)、`010-0700-0001`/`0002`(GuardianChild)、`010-0800-0001`(TeacherChild)、`010-0900-0001`~`0003`(SecurityAudit)、`010-9999-0001`(PhoneUniqueness)、`010-9999-0002`(PushSubscriptionConstraint)、`010-0000-8801`/`8802`(PushSubscriptionApi)、`010-0001-0001`~`0005`(AdminApproval)、`030-0001-0001`~`0003`(PlatformAdminApproval)

**login_id**(测试用 `ON CONFLICT DO UPDATE` upsert,幂等;但 seed 最好不占用):`baseline-test-auth-user`、`tenant-iso-test-user`、`teacher-assignment-test-user`、`teacher-room-test-user`、`tc-teacher`、`gc-guardian`、`gc-admin`、`audit-director`、`audit-teacher`、`audit-platform-it`、`evrev-{admin,teacher,guardian,foreign-admin}`、`detect-staff-admin`、`admin-appr-*`、`plt-appr-*`、`pushsub-*`、`inc001-phone-*`

## D. FK 完整性(容器启动门槛)

- D-1 全部 seed 按文件编号(= FK 拓扑序)INSERT 必须成功、零 FK 违例,容器才能起来。`ContextLoadSmokeTest` / `FlywayMigrationSmokeTest` 及所有集成测试都以此为前提。

## E. 仅依赖 schema、不依赖 seed 数据值(重写时无需特别照顾)

- `SchemaConsistencyGuardTest`(纯 information_schema 结构断言)
- `FlywayMigrationSmokeTest`(只看 flyway_schema_history baseline/V2-V6)
- `SecurityBoundaryIntegrationTest`(白名单/匿名访问 401/403)
- `ErrorResponseSensitiveDataIntegrationTest`(canary,自建请求)
- `PhoneUniquenessConstraintTest` / `PushSubscriptionConstraintTest` / `PushSubscriptionApiTest`(纯自建,仅需 phone 不冲突 = C)
- `ContextLoadSmokeTest`(仅 D-1)

---

**最小化重写结论**:保住 A-1/A-2、B-1~B-10、避开 C、满足 D-1,其余大量业务行(复杂 guardian/teacher/children 名册、多余 detection 行等)均可大幅精简。`room_type` 等脏值可放心净化(无测试断言其值)。
