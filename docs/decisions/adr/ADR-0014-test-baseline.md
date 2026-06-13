---
ADR: ADR-0014
title: "ADR-0014: 建立测试基线（Test Baseline）"
status: Implemented
date: 2026-06-07
implemented: 2026-06-08
deciders: 接手人起草（2026-06-07），维护者 Accept（2026-06-07）；Implementation session 落地（2026-06-08）
---

# ADR-0014: 建立测试基线（Test Baseline）

> **前瞻提案**（非回溯）。源于 2026-06-07 接手复核：现有 roadmap 把"搭建测试基础设施"埋为 [ADR-0009](ADR-0009-restore-auth-enforcement.md) 的子任务（最后一篇），但其前面的 [ADR-0012](ADR-0012-production-data-lifecycle.md)/[ADR-0013](ADR-0013-dictionary-tables-governance.md)/[ADR-0010](ADR-0010-rrn-one-way-hash.md) 均为高 blast-radius 的 schema/安全变更，将在**无回归保护**下进行。本 ADR 把测试基线抽离为**独立的、最先落地的一步**。

## 状态（Status）

**Implemented（2026-06-08）**。Accepted 2026-06-07；由独立 Implementation session 于 2026-06-08 落地。

落地产物：
- `backend/build.gradle`：Testcontainers BOM + `junit-jupiter` + `postgresql`；2026-06-11 通过 Spring Boot BOM 的 `testcontainers.version` property 将运行时依赖对齐至 `1.21.4`，并兼容近期 Docker Engine
- `backend/src/test/java/com/ai_kids_care/BaseIntegrationTest.java`：基类（Testcontainers PG + initdb 挂载 + DynamicPropertySource）
- `backend/src/test/java/com/ai_kids_care/v1/auth/AuthEndpointTest.java`：19 个认证/公开注册集成测试（2026-06-13 增补显式 401 契约）
- `backend/src/test/java/com/ai_kids_care/v1/detection/DetectionEventEndpointTest.java`：2 个检测事件关闭路径 404 测试
- `backend/src/test/java/com/ai_kids_care/v1/contract/`：Phase 1A/1B 增补公共 JSON/OpenAPI、敏感字段和关闭端点契约门禁
- `backend/src/test/java/com/ai_kids_care/v1/service/AuthServiceRegistrationTest.java`：7 个公开注册与认证失败契约单元测试
- `backend/src/test/resources/application-test.yml`：测试 profile（排除 Neo4j 自动配置）
- `Jenkinsfile`：`Test` stage 插入 `Docker Compose Up` 之前
- `.github/workflows/backend-java-tests.yml`：2026-06-11 补充 GitHub Actions Java 21 + Gradle + Testcontainers 测试门禁
- `docs/engineering/testing.md`：全量重写

临时限制（2026-06-11）：`FlywayMigrationTest` 在 ADR-0013 落地前以 `@Disabled` 保留。Flyway V1 按已接受目标不创建 `common_codes`，但遗留 `CommonCode` 实体仍参与 `ddl-auto=validate`；ADR-0013 删除该映射后必须立即恢复此门禁，不得用新增 `common_codes` 迁移规避。

## 背景（Context）

✅ **事实**：当前三端均无自动化测试（OQ-TEST-1）。
- `backend/src/test/` 为空目录；`build.gradle:39-40` 虽已声明 `spring-boot-starter-test` 与 `spring-security-test`，但无任何测试类。
- 前端、AI 亦无测试。

✅ **事实（矛盾点）**：[roadmap.md](../../modernization/roadmap.md) 一方面把"搭建 Spring Boot Test 基础设施"列在 ADR-0009 清单（最后一项），另一方面声称"前面 ADR 落地时已具备测试基线"——二者只有在**测试设施先于 0012 建成**时才能同时成立。

🔶 **推断**：按现 roadmap 次序，0012（改 schema 创建方式）、0013（删 8 个后端文件 + 2 张表）、0010（改 RRN 读写路径 + 存量数据迁移）这三步——恰是最该被 characterization 测试护住的高风险改动——会在零回归保护下进行。

✅ **约束（来自 `CLAUDE.md` 测试规则）**：修改代码时须"为遗留行为补 characterization 测试""保持既有行为"。这要求测试设施**先于**任何行为/schema 变更存在。

✅ **约束（团队形态）**：项目已转为**单人维护 + AI Agents**（见根 README）。无测试时，AI 辅助改动缺乏可执行的安全网——测试基线是使该协作模式可持续的前提，而非可选项。

✅ **约束（技术）**：后端 `application.yml:16` 为 `ddl-auto: validate`，schema 由 `db/initdb/*.sql` 在容器启动时建立；schema 重度使用 PostgreSQL 特性（enum、复合外键、`timestamptz`）。这决定了测试若要忠实反映生产、并同时守护 `validate`，需对接**真实 PostgreSQL**而非内存库替身。

## 决策（Decision）

把"测试基线"作为一个**独立的、最小自洽的 Implementation 步骤**优先交付，**插入 [ADR-0011](ADR-0011-extract-codegen-subproject.md)（已完成）之后、[ADR-0012](ADR-0012-production-data-lifecycle.md) 之前**，而非并入 ADR-0009。范围刻意收窄为"薄而可工作的基线"：

1. **后端集成测试骨架**：JUnit 5（已具）+ **Testcontainers PostgreSQL** + `spring-security-test`。提供一个基类，针对一次性 Postgres 容器启动 Spring 上下文，并用真实 `db/initdb/*.sql` 建库——**顺带持续校验 `ddl-auto=validate` 与 initdb 脚本的一致性**。
2. **首批 characterization 测试**（确立范式，非追求覆盖率）：覆盖**已真实实现**的稳定行为——4 个认证端点（`POST /auth/login`、`/refresh`、`/register`、`GET /auth/register/availability`）+ 2~3 个代表性 CRUD 读路径。
3. **接入构建与 CI**：`./gradlew test` 纳入门禁；`Jenkinsfile` 在部署 stage **之前**加测试 stage，失败即阻断。
4. **约定文档对齐**：与 [docs/engineering/testing.md](../../engineering/testing.md) 的测试分层/命名约定保持一致。

**显式不在范围内**（留作后续）：全量覆盖率、前端/AI 测试栈、契约/E2E 测试、突变测试。后续每篇 ADR（0012/0013/0010/0009）落地时，**必须为其改动的行为补 characterization 测试**——本 ADR 只负责把"地基与范式"立起来。

## 后果（Consequences）

- **正面**：
  - 为后续所有加固（0012/0013/0010/0009）提供回归安全网，使"单人 + AI Agents"的改动模式可持续。
  - Testcontainers 复用真实 schema → 测试运行本身即对 `ddl-auto=validate`、`initdb` 脚本、JPA 实体映射做持续验证。
  - 为 [OQ-ARCH-4](../../modernization/open-questions.md)（keyword 空操作）等"首个带测试的真实改动"提供落点。
- **负面 / 代价**：
  - Testcontainers 依赖 **CI 节点可用 Docker**（Jenkins agent 需 docker socket / DinD）——一项基础设施前置；测试比内存库慢、CI 时长上升。
  - 初始搭建有成本（测试基类、容器复用、数据准备）。
- **影响范围**：`backend/build.gradle`（依赖大体已具）、新增 `backend/src/test/**`、`Jenkinsfile`（test stage）、[docs/engineering/testing.md](../../engineering/testing.md)。

## 考虑过的备选（Alternatives Considered）

- **H2 内存库替代 Testcontainers** — 否决：schema 用 PG enum / 复合外键 / `timestamptz`，H2 兼容模式语义偏离，既无法忠实反映生产，也无法守护 `ddl-auto=validate` 与 initdb 脚本（而 0012 Flyway 迁移的正确性恰恰依赖这一点）。
- **把测试设施留在 ADR-0009 内（现状 roadmap）** — 否决：使 0012/0013/0010 在无回归保护下进行，且与 roadmap 自述"前面 ADR 已具测试基线"自相矛盾。
- **加固全部做完再补测试** — 否决：违反 `CLAUDE.md`"改既有行为前先补 characterization 测试"。
- **仅单元测试（Mockito，不起容器）** — 部分采纳为补充：对纯逻辑有用，但覆盖不到 JPA/SQL/schema 这一**真实风险所在层**（迁移、RRN 查询改写）。故作为补充而非基线主路径。

## 关联（References）

- 解除 [ADR-0009](ADR-0009-restore-auth-enforcement.md) 的测试前置依赖（0009 落地时该项已就绪）。
- [ADR-0012](ADR-0012-production-data-lifecycle.md)（首个受益者：Flyway 迁移需要测试守护）、[ADR-0004](ADR-0004-layered-backend-codegen.md)（codegen 产物的可测试性）。
- [open-questions.md](../../modernization/open-questions.md)：OQ-TEST-1、OQ-ARCH-4。
- [docs/engineering/testing.md](../../engineering/testing.md)、[roadmap.md](../../modernization/roadmap.md)。
- 代码：`backend/src/test/`（空）、`backend/build.gradle:39-40`、`application.yml:16`、`db/initdb/01_create_schema.sql`。
