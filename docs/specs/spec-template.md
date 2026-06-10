---
id: SPEC-NNNN
title: "<short capability title>"
status: Draft
owner: "<owner>"
created: YYYY-MM-DD
updated: YYYY-MM-DD
related_adrs: []
---

# SPEC-NNNN: <Title>

## 目标结果（Outcome）

用一段话描述用户或运维最终获得的结果。

## 当前事实（Current Facts）

- 带文件、schema、API 或测试证据的实现事实。
- 已知漂移、约束与风险。

## 范围（Scope）

### In Scope

- 本次必须新增或修改的具体行为。

### Out of Scope

- 明确排除项。

## 角色与权限（Actors And Permissions）

| Actor | 允许行为 | Tenant / 数据边界 |
| --- | --- | --- |
| `<role>` | `<behavior>` | `<boundary>` |

## 业务流程（Business Flow）

1. 触发条件。
2. 校验与授权。
3. 状态转换。
4. 副作用与通知。
5. 失败、补偿与重试。

## 契约（Contracts）

描述 API、event、schema、UI 与配置变化。优先链接生成的 contract，不手抄大段 schema。

## 不变量（Invariants）

- 必须始终成立的安全、隐私、幂等、事务与生命周期规则。

## 验收标准（Acceptance Criteria）

- [ ] 可观察且有预期结果的标准。
- [ ] 负向或权限标准。
- [ ] 适用时包含迁移与兼容性标准。

## 验证（Verification）

| 检查 | Command / Test | 预期结果 |
| --- | --- | --- |
| `<check>` | `<command>` | `<result>` |

## 开放问题（Open Questions）

- 问题、负责人和决策期限。

## 实施记录（Implementation Notes）

仅在 Approved 后填写：改动范围、rollout 说明与验证证据。
