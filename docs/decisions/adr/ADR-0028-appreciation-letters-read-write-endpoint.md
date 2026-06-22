---
ADR: ADR-0028
title: "ADR-0028: 重新开放感谢信读写端点（服务端派生身份）"
status: Accepted
implementation: Implemented
date: 2026-06-19
deciders: 维护者（2026-06-19 决策新建感谢信后端端点并批准本 ADR + SPEC-0003 scope/授权模型/OQ 全默认）
---

# ADR-0028: 重新开放感谢信读写端点（服务端派生身份）

> **前瞻提案**。维护者于 2026-06-19 决定新建感谢信（appreciation letters）后端读写端点（backlog **BE-5**，规范见 [SPEC-0003](../../specs/SPEC-0003-appreciation-letters-read-write.md)）。本 ADR 形式化"重新开放"决策，并正式 **supersede 此前"移除并锁死感谢信写路径"的安全加固态势**。

## 状态（Status）

Accepted（2026-06-19 维护者签署；落地经发布门 + 独立评审）

## 背景（Context）

- 感谢信后端骨架完整存在（entity/repo/mapper/VO/enum/表/seed），但写路径在早前安全收敛中被**有意移除并以守卫测试锁死**：
  - `service/AppreciationLetterService` 仅余 `list`/`get`，且均 `@PreAuthorize("denyAll()")`；无 Controller、无 DTO、无写方法。
  - `SensitiveWriteContractTest` 断言 `AppreciationLetterCreateDTO/UpdateDTO`、`Service.create/update/delete*`、`Mapper.toEntity/updateEntity` **缺席**。
  - `PublishedOpenApiContractTest` 断言路径 `/api/v1/appreciation_letters`(+`/{id}`) 与两个 DTO 组件 **缺席**。
- 该锁死态势属于 SPEC-0001 安全边界工作的一部分（"未经授权的写面一律关闭"）。**它是一项已生效的加固决策**，不得静默改写——故以本 ADR supersede。
- 业务需求（维护者 2026-06-19）：家长需能向老师/园所写感谢信并查看，构成真实业务价值；当前前端整簇处于 `unavailable` 占位态（FE-3 已特意保留以备激活）。
- 既有授权范式可直接复用：薄 Controller + Service 层 `@PreAuthorize` + `EffectiveAuthorizationContext` 服务端派生租户/身份（[ADR-0019](ADR-0019-effective-authorization-context-tenant-enforcement.md)）+ Repository SQL 细粒度作用域 + 隐藏 404/审计（参照 `ChildrenController`）。多租户隔离依 [ADR-0003](ADR-0003-multitenancy-kindergarten-id.md)。

## 决策（Decision）

重新开放感谢信 **读写端点**，作为一个**最小自洽的安全交付**，而非简单恢复被删代码：

1. 发布 `/api/v1/appreciation_letters` 的 `GET`(列表/详情)、`POST`(create)、`PUT /{id}`(update)、`DELETE /{id}`(delete)。
2. **sender 与 tenant 一律服务端派生**（来自会话 `EffectiveAuthorizationContext`）；客户端提交的 `senderUserId`/`kindergartenId`/`status` 一律忽略。新 DTO 不含这些字段。这是与旧（被移除）实现的关键差异，也是 FE-2 的前提。
3. 授权模型 = "**家长写 · 租户内可见**"（角色矩阵见 SPEC-0003 §角色）：GUARDIAN 写本租户、作者改/删本人；读按 public/作者/对象/园所管理员分级；跨租户隐藏 404。
4. **翻转三个守卫契约测试**为正向断言，并补正向授权/契约测试（伪造身份被忽略、跨租户拒绝、隐藏 404）。
5. 响应 VO 按 SPEC-0001 收敛 S0/S1（最小暴露：以实名/显示名替代裸内部 ID，详见 SPEC-0003 OQ-1）。

## 后果（Consequences）

- **正面**：交付真实业务价值；激活已保留的前端簇；以服务端派生身份纠正旧实现"客户端自报 sender/tenant"的越权隐患；为 FE-2 提供干净契约。
- **负面 / 代价**：
  - 新增公开写面 = 新攻击面；必须配套完整授权/契约测试，否则回归风险高。
  - 翻转守卫测试削弱了"全写面关闭"的简单不变量；以更细的正向授权测试替代，复杂度上升。
  - 若 OQ-1（S0/S1 暴露）处理不当，可能在响应中泄露内部用户标识。
- **影响范围**：`controller/AppreciationLetterController`(新)、`service/AppreciationLetterService`、`dto/AppreciationLetter{Create,Update}DTO`(新)、`mapper/AppreciationLetterMapper`、`vo/AppreciationLetterVO`、`contract/SensitiveWriteContractTest` + `PublishedOpenApiContractTest`、OpenAPI、(随后)前端 FE-2。

## 考虑过的备选（Alternatives Considered）

- **维持锁死、不开放** — 否决：与维护者已确认的业务需求冲突；前端簇永久占位。
- **恢复被移除的旧实现（客户端传 sender/tenant）** — 否决：那正是越权隐患来源；与 SPEC-0001 服务端派生身份原则矛盾。
- **仅开放只读（不开写）** — 否决：无法满足"家长写感谢信"的核心场景；写面才是业务价值所在。

## 关联（References）

- 规范：[SPEC-0003](../../specs/SPEC-0003-appreciation-letters-read-write.md)（BE-5）。
- supersede：此前由 `SensitiveWriteContractTest` / `PublishedOpenApiContractTest` 锁死的"感谢信写面关闭"加固态势（SPEC-0001 安全边界工作的一部分）。
- 复用：[ADR-0019](ADR-0019-effective-authorization-context-tenant-enforcement.md)（服务端派生授权上下文）、[ADR-0003](ADR-0003-multitenancy-kindergarten-id.md)（kindergarten_id 多租户）、[ADR-0009](ADR-0009-restore-auth-enforcement.md)（鉴权强制基线）。
- 代码：`AppreciationLetterService.java`、`AppreciationLetterVO.java`、`AppreciationLetter.java`、`ChildrenController.java`（范式）、`SensitiveWriteContractTest.java:84-85,246-248,276-277`、`PublishedOpenApiContractTest.java:269-270,377-378`。
- 后续依赖：backlog **FE-2**（前端按本契约移除身份字段）。
