## Why

迁移（Change 2）拆除了三道 data-platform 护栏，至今仅作为 backlog 记在 `rebuild-guardrails` spec 里、无任何回归保护：

- **INC-003**：Neo4j loader 不得把 S0/PII 字段投影进图（password_hash/rrn_*/birth_date/address/email/phone/…）。loader 当前实现 CLEAN，但**没有测试阻止回归**——某次给 `SET` 加一行 PII 就会静默泄露。
- **INC-005**：MapStruct mapper 不得静默丢弃未映射 target。旧守卫是全局 build flag `-Amapstruct.unmappedTargetPolicy=ERROR`，被移除；22 个 mapper 现全用默认 WARN，新增实体字段忘了映射不会报错。
- **schema-digest 漂移**：schema 三件（`schema.dbml` / `initdb/01_create_schema.sql` / Flyway 迁移）一致性无守卫。已发现既有漂移：`schema.dbml` 的 `sent_at` 仍 `NOT NULL`（V3 已 relax）。

后端测试地基与 CI 门已就绪（上一批 change），现把这三道护栏按 `testing-and-ci`/`rebuild-guardrails` 哲学**重建为能力测试/编译期守卫**（不恢复被禁的全局 build flag、不重建独立 harness 层），清空护栏 backlog。三者都落在 backend Gradle 测试 + 同一 CI 门，内聚。

## What Changes

- **INC-003 loader PII 扫描守卫**（重建为 backend 能力测试）：新增一个自包含测试，正则扫描 `db/ne4j_kindergartens/*.py` 的 Cypher 属性绑定（`<var>.<field> = $<param>`），断言禁字段集合（password_hash/email/phone/address/contact_*/rrn_encrypted/rrn_first6/emergency_contact_*/birth_date/stream_password_*）不出现；并断言扫到的 loader 文件非空（防空过）。纯文本扫描，无需 Python/Neo4j 运行时。
- **INC-005 MapStruct 未映射守卫**（每-mapper 编译期）：给 22 个 `@Mapper` 加 `unmappedTargetPolicy = ReportingPolicy.ERROR`，并为有意留空的 target 显式 `@Mapping(target=..., ignore=true)`。未映射 target → **编译失败** → `./gradlew test` CI 门拦住。这是 idiomatic MapStruct 配置，非 testing-and-ci 禁的全局 build flag。补一个最小映射往返测试坐实一两个核心 mapper 的完整性。
- **schema-digest 漂移守卫**（重建为 Testcontainers 结构断言测试）：新增 backend 测试，对 Flyway 全量迁移后的真实 schema 做结构断言（关键表存在/不存在、关键列可空性、枚举），并断言 `initdb/01_create_schema.sql` 与 `V1__initial_baseline.sql` 结构一致（initdb=V1 镜像不变量）。**顺带修复测试暴露的既有 dbml 漂移**（`sent_at`/`fail_reason` 等与 V3+ 对齐；device_tokens→push_subscriptions 已在 V7 同步）。
- **更新 `rebuild-guardrails` spec**：三项已重建，从 backlog 移除 → 护栏 backlog 清空。
- **更新 `data-platform` spec**：新增「mapper target 完整性守卫（INC-005）」与「schema 源文件一致性守卫」两条 requirement（INC-003 已有 requirement，现由测试强制）。

Non-goals：
- **不**恢复全局 `-Amapstruct.unmappedTargetPolicy=ERROR` build flag（testing-and-ci 明令禁止）。
- **不**重建旧 harness 层（HarnessChecks/HarnessTestSupport/schema-digest.sh/digest.md/drift CI）——按能力测试重建，不要独立守卫层。
- **不**改 loader 运行时架构（CSV 快照 vs 实时 PG / ADR-0002）——守卫只扫源文，与该架构无关。
- **不**改产品行为；除 mapper 注解（为消除未映射）外不动业务逻辑；schema 修复仅对齐源文与迁移、不改运行时 schema。

## Capabilities

### New Capabilities
（无：均属既有 data-platform / rebuild-guardrails 能力）

### Modified Capabilities
- `rebuild-guardrails`: 「Guardrail backlog content」requirement —— 移除已重建的三项（INC-003/INC-005/schema-digest），backlog 清空；保留「护栏以 TDD 重建为能力测试」的耐久原则。
- `data-platform`: 新增 INC-005 mapper 完整性守卫 requirement、schema 源文件一致性守卫 requirement；INC-003 既有 requirement 现由能力测试强制（补充 scenario）。

## Impact

- **新增测试**：`backend/src/test/java/...`——loader PII 扫描测试、schema 结构断言测试（Testcontainers）、mapper 完整性往返测试。
- **产品代码**：22 个 `@Mapper` 注解加 `unmappedTargetPolicy=ERROR` + 补 `ignore=true`（为消除编译期未映射；需逐 mapper 审有意留空的 target，**这是本 change 主要风险点**——误加 ignore 会掩盖真实漏映射，故每处都要核对源/目标字段）。
- **schema 源文**：`db/dbml/schema.dbml`（+ 可能 `initdb`）对齐 V2+ 迁移漂移（由 schema-digest 测试暴露的项）。
- **CI**：复用既有 `backend-java-tests.yml` 门；无新 workflow。
- **spec**：`rebuild-guardrails` + `data-platform` delta（随 change，archive 时 sync）。
- **验证前提**：Testcontainers 需 Docker（本机容器内 `gradle:8.7-jdk21` + DinD 实跑，CI runner 自带）。
