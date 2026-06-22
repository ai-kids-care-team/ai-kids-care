---
ADR: ADR-0027
title: "ADR-0027: 退役 pg-spring-crud-codegen 一次性 CRUD 代码生成器"
status: Accepted
implementation: Implemented (2026-06-18)
date: 2026-06-18
deciders: 维护者（2026-06-18 口头授权移除；commit 123c57e）
supersedes: [ADR-0011]
superseded_by: null
related_specs: [SPEC-0001]
---

# ADR-0027: 退役 pg-spring-crud-codegen 一次性 CRUD 代码生成器

## 状态（Status）

**Accepted（2026-06-18）；已实现（commit 123c57e，`pg-spring-crud-codegen/` 目录已删除）**。**Supersedes [ADR-0011](ADR-0011-extract-codegen-subproject.md)**（ADR-0011 状态更新为 Superseded by ADR-0027）。

## 背景（Context）

`pg-spring-crud-codegen/` 是一个一次性 CRUD 脚手架（Python + pystache + Mustache 模板）。其历史沿革：

- 原位于 `scripts/codegen/`，由 ADR-0004 描述；于 2026-05-29 经 ADR-0011 迁入 `pg-spring-crud-codegen/`，定位为"日后可独立拆仓"的子工程。
- 工具流程：内省 PostgreSQL schema（`introspect_pg.py`）→ 构建 `EntityModel`（`model.py` + `naming.py` + `type_map.py`）→ pystache 渲染 6 个 Mustache 模板（`CreateDTO`、`UpdateDTO`、`Mapper`、`VO`、`Controller`、`Service`）→ 输出 Java CRUD 骨架。

退役的原因：

1. **SPEC-0001 合规性**：代码生成器的默认产物违反 SPEC-0001 中的多租户与角色 scope 要求——生成的 Controller 缺 `@Valid`/`@PreAuthorize`，Service 使用无租户过滤的 `findAll`，VO 平铺所有字段（含 S0/S1 敏感字段）。这与当前已强制落地的鉴权机制（ADR-0009/ADR-0019）不兼容。
2. **无双向绑定**：现有后端代码已手工演进（如 `AuthService` 复杂注册逻辑、`GraphRepository` 图查询、租户 scoped 查询），与模板之间不存在双向绑定；重新生成没有实际价值，反而存在产出不合规骨架的风险。
3. **替代方案已就位**：
   - `docs/engineering/backend-crud-layering-reference.md` 已从 6 个 Mustache 模板回收有效分层契约，并加固了安全默认（含 `@PreAuthorize`、租户过滤、VO 字段收窄），覆盖了 codegen 的参考价值。
   - `.claude/skills/authz-read-slice/SKILL.md` 提供含 action+gate、scoped JPQL、审计拒绝、契约测试的完整读端点执行清单，满足新增端点的需求。
4. **维护者意向**：维护者已口头授权移除，无计划将其拆出为独立仓库（与 ADR-0011 的"日后拆仓"预期不符）。

## 决策（Decision）

退役并删除 `pg-spring-crud-codegen/`（整目录：Python 脚本、Mustache 模板、README、docker-compose 等）与 `scripts/codegen/README.md`（软指针 stub）。

新增后端领域对象时改用**手写 + 参考文档**：

- 分层骨架与安全默认：`docs/engineering/backend-crud-layering-reference.md`（含 `@PreAuthorize`、租户过滤、VO 字段收窄等加固）
- 含完整 authz gate + scoped JPQL + 契约测试的读端点：`.claude/skills/authz-read-slice/SKILL.md`

**明确边界**：ADR-0004 的「分层契约」原则（Controller/Service/Repository/DTO/VO/MapStruct）**仍然有效**。本 ADR 只退役 codegen 工具本身，不否定分层架构，不改变 DB-first 的数据模型策略。

## 方案比较（Alternatives Considered）

| 方案 | 描述 | 结论 |
| --- | --- | --- |
| **A（采用）** | 直接删除，手写 + 参考文档替代 | 零遗留复杂度，SPEC-0001 合规，替代方案已成熟 |
| B | 保留模板，强制加入 SPEC-0001 安全默认（`@PreAuthorize`、租户过滤、VO 收窄） | 维护成本高：需追踪模板与 SPEC-0001 的漂移；投入产出不成比例 |
| C | 拆出独立仓库（ADR-0011 的原始远期目标） | 维护者已明确无此意图；与项目当前规模不匹配 |

## 后果（Consequences）

**正面**：

- 消除 SPEC-0001 不合规的代码生成路径，防止未来意外调用后产出不安全骨架。
- 减少导航噪音（`pg-spring-crud-codegen/` 是完全静态的工具，不参与任何运行时或 CI 流程）。
- 仓库结构更清晰，新维护者不会误把 codegen 视为活跃工具。

**负面 / 代价**：

- 无自动化脚手架，新增 CRUD 域对象需手写或参照参考文档（`backend-crud-layering-reference.md`）。
- 对历史性 `git log` 路径无影响（git 历史保留，`pg-spring-crud-codegen/` 仍可从历史提交访问）。

**影响范围**：`pg-spring-crud-codegen/`（删除）、`scripts/codegen/README.md`（删除）、各引用文档（更新注记）、ADR-0011（加 `superseded_by`）。

## 合规与验证（Compliance & Verification）

执行后应满足：

- `git grep -n 'pg-spring-crud-codegen'` 结果仅包含 ADR-0004、ADR-0011、ADR-0027 内的历史性叙述行（含注记）；无任何描述其为「当前工具」或「可运行」的有效引用。
- `git grep -n 'scripts/codegen'` 结果仅包含 ADR-0011、ADR-0027（历史路径叙述）以及 open-questions.md OQ-ARCH-3 的历史结论行；无指向 `scripts/codegen/README.md` 的有效链接。
- `git diff --check` 无空白错误。

## 关联（References）

- [ADR-0011](ADR-0011-extract-codegen-subproject.md)（被本 ADR 取代）
- [ADR-0004](ADR-0004-layered-backend-codegen.md)（分层架构原则，本 ADR 不改变）
- [ADR-0009](ADR-0009-restore-auth-enforcement.md)、[ADR-0019](ADR-0019-effective-authorization-context-tenant-enforcement.md)（与 codegen 产物不兼容的鉴权约束）
- [docs/engineering/backend-crud-layering-reference.md](../../engineering/backend-crud-layering-reference.md)（手写替代方案）
- [`.claude/skills/authz-read-slice/SKILL.md`](../../../.claude/skills/authz-read-slice/SKILL.md)（读端点完整执行清单）
