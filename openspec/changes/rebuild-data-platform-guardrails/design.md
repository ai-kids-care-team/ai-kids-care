## Context

Change 2 拆除的三道 data-platform 护栏至今只在 `rebuild-guardrails` spec 当 backlog、无回归保护。apply 期已详测现状：

- **INC-003**：loader = Python（`db/ne4j_kindergartens/no*.py`），当前 SET 子句 CLEAN（禁字段仅出现在 CSV 注释/RETURN，不作 `= $param` 绑定）。旧守卫是一个 backend JUnit 纯文本正则扫（`HarnessChecks.forbiddenLoaderProjections`），不连 DB/不跑 loader。
- **INC-005**：22 个 `@Mapper(componentModel="spring")` 均无 `unmappedTargetPolicy`（默认 WARN）。旧守卫是被移除的全局 build flag（testing-and-ci 明令禁止恢复）。各 mapper 已大量用 `@Mapping(ignore=true)` 标注有意留空（id/createdAt/关系实体等）。
- **schema-digest**：旧守卫是脚本生成 `docs/engineering/schema-digest.md` + CI `git diff` 比对（已删）。三件源（dbml/initdb/migrations）一致性无守卫。已发现既有漂移：`schema.dbml` `sent_at`/`fail_reason` 仍 `NOT NULL`（V3 relax 未回填 dbml）。

约束：后端测试地基（Testcontainers `BaseIntegrationTest` + initdb+baseline+V2..V7+validate）与 CI 门 `backend-java-tests.yml` 已就绪；`testing-and-ci` 禁全局 mapstruct build flag、禁独立 harness 守卫层；新体制取舍记 design（不另起 ADR）。

## Goals / Non-Goals

**Goals:**
- 三道护栏各自重建为能力测试/编译期守卫，纳入既有 backend CI 门，清空护栏 backlog。
- 守卫语义忠实于原 INC：loader 不泄 PII、mapper 不静默漏映射、schema 源文不漂移。

**Non-Goals:**
- 不恢复全局 `-Amapstruct.unmappedTargetPolicy=ERROR`；不重建 harness 层（HarnessChecks/schema-digest.sh/drift CI）。
- 不改 loader 运行时架构（ADR-0002 / CSV vs 实时 PG）；不改业务行为；schema 修复仅对齐源文与迁移。

## Decisions

### D1：INC-003 重建为 backend 自包含正则扫描测试（沿用旧守卫机制，去 harness 依赖）
新增 `LoaderPiiProjectionGuardTest`（如 `v1/dataplatform/`），读 `db/ne4j_kindergartens/*.py` 文本，对每文件套正则 `\b[A-Za-z_]\w*\.(<forbidden>)\s*=\s*\$[A-Za-z_]` 找 Cypher 属性绑定违例；断言 `violations.isEmpty()` 且 `loaderFiles.isNotEmpty()`（防空过）。禁字段集合取自 data-platform spec INC-003 requirement。
- 路径解析：`../db/ne4j_kindergartens`（相对 backend gradle 工作目录，与 BaseIntegrationTest 的 `../db/initdb` 同法）。纯文本、无 Spring/DB。
- 备选：Python pytest —— 否决，loader 无 pytest harness、CI 无该路径，且会引入第二个测试 runtime；Java 文本扫零新依赖、复刻旧守卫。

### D2：INC-005 重建为每-mapper 编译期 `unmappedTargetPolicy=ERROR`（+ 最小往返测试）
给 22 个 `@Mapper` 加 `(unmappedTargetPolicy = ReportingPolicy.ERROR)`；为有意留空的 write-path target 显式 `@Mapping(target=..., ignore=true)`。未映射 → 编译失败 → `gradle test` 门拦截（编译是 test 前置）。
- 这是 idiomatic MapStruct 配置（非全局 build flag），符合 testing-and-ci 字面与精神（守卫由测试门强制，不在 build.gradle 加 harness arg）。
- **主要风险**：逐 mapper 审「未映射 target」时，须区分「有意留空（加 ignore）」与「真漏映射（补 @Mapping）」。误加 ignore 会掩盖真实缺陷。缓解：逐 mapper 对照 源 DTO/实体 与 目标 字段；ERROR 编译失败会强制把每个未映射 target 摊到眼前显式决断；往返测试对 1–2 个核心 mapper（如 NotificationMapper / 某 entity↔DTO）断言非 ignore 字段映射后非空。
- 备选：生成码扫描测试（脆，依赖 MapStruct 生成注释格式）/ 纯往返测试（噪声大、需逐 mapper setup）——均否决为主守卫，往返仅作补充。

### D3：schema-digest 重建为 Testcontainers 结构断言测试（+ 修 dbml 漂移）
新增 `SchemaConsistencyGuardTest extends BaseIntegrationTest`：对 Flyway 全量迁移后的真实 schema 用 `information_schema`/`pg_catalog` 断言关键结构不变量：
- `push_subscriptions` 存在、`device_tokens`/`device_platform_enum` 不存在（V7 生效）；
- `notifications.sent_at`/`fail_reason` 可空（V3 生效）、`retry_count` 有默认；
- 关键枚举/唯一索引存在（uq_notifications_dedupe、uq_push_subscriptions_*、uq_user_account_phone 等代表项）。
并加 `initdb/01_create_schema.sql` 与 `V1__initial_baseline.sql` 的结构一致性断言（initdb=V1 镜像不变量；用规范化文本/对象比较，容忍注释/空白差异）。
- **修复**：测试会暴露 `schema.dbml` 的 `sent_at`/`fail_reason` 漂移（与 V3 不符）等——本 change 回填 dbml 使其与 V2+ 迁移对齐（dbml 是 DB-first 源，应反映最新期望 schema）。修复范围 = 测试实际暴露的项，apply 时枚举。
- 备选：复活 digest.md + CI diff 脚本——否决（已删、属 harness 层）；纯 dbml↔initdb 文本 diff——作为 V1 镜像断言的一部分，但主守卫是「迁移后真实 schema 结构断言」（迁移是 SoR）。

### D4：测试归属与运行
三项均 backend Gradle 测试、走 `backend-java-tests.yml` 门。INC-003/往返测试为纯单测（快）；schema 断言为 Testcontainers 集成测试（复用共享容器）。本机用 `gradle:8.7-jdk21` 容器 + DinD 实跑验证。

## Risks / Trade-offs

- [22 个 mapper 加 ERROR 后大量编译失败，误加 ignore 掩盖真漏映射] → 逐 mapper 对照字段、小步编译；优先用 `@Mapping` 补真实映射，仅对确属有意留空者 ignore；高风险 mapper（写路径）重点核。
- [schema 断言测试暴露的 dbml 漂移多于预期，修复面扩大] → 修复限于「源文对齐迁移」的纯文档/DDL 改动，不动运行时 schema；若漂移项过多，按 spec 把次要项记为 follow-up（no silent cap：log 出未修项）。
- [initdb=V1 镜像的文本一致性断言过脆（注释/格式差异）] → 比较规范化后的结构（表/列/类型/约束），非逐字 diff。
- [loader 正则误报/漏报] → 复刻旧守卫已验证的正则（只匹配 `=$param` 绑定，跳过注释/RETURN/SQL 位置参数）。
- [Testcontainers 需 Docker] → 容器内实跑；CI runner 自带。

## Migration Plan

1. INC-003：写 loader PII 扫描测试（当前 loader CLEAN → 应即绿；如变红说明发现真实泄露，单列）。
2. INC-005：逐 mapper 加 `unmappedTargetPolicy=ERROR` + 补 `ignore`/`@Mapping`，容器内反复编译至全过；加 1–2 个往返测试。
3. schema-digest：写结构断言 + V1 镜像断言测试；按测试暴露回填 dbml 漂移至绿。
4. 容器内全套件全绿；rebuild-guardrails + data-platform spec delta；code review；合 develop / archive。
- 回滚：测试与注解可 git 还原；schema 源文修复是对齐性改动，无运行时风险。

## Open Questions

- INC-005 往返测试覆盖到哪几个 mapper 为「够」？倾向核心写路径 mapper 各一个，apply 第 2 步定。
- schema 断言的「关键结构」清单粒度（断言全部 28 表 vs 代表性子集）？倾向代表性 + 近期迁移（V3/V7）触及项，apply 第 3 步定。
- dbml 漂移若涉及 V2/V4/V5/V6（非仅 V3）的项，是否全部本期回填？倾向全部回填（dbml 应为最新期望源），但以测试暴露项为准、log 未决项。
