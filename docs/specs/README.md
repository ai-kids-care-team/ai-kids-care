# Specs

`docs/specs/` 存放面向未来、可评审的行为契约。Spec 在实现开始前描述**系统必须满足什么**；它不是进度日志，也不替代 ADR。

## 索引

| ID | 标题 | 状态 | Implementation |
| --- | --- | --- | --- |
| [SPEC-0001](SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md) | 认证、授权、租户与敏感数据边界 | Approved | Partial |
| [SPEC-0002](SPEC-0002-admin-management-approval-endpoints.md) | Admin 管理与审批端点（角色/用户/membership 审批与状态变更） | Approved | Partial |

## 生命周期

| Status | 含义 |
| --- | --- |
| `Draft` | 问题与范围仍在变化 |
| `Review` | 已可交给维护者评审 |
| `Approved` | 可以进入 Implementation |
| `Implemented` | Acceptance Criteria 已验证 |
| `Superseded` | 已被另一份 Spec 取代 |

## 命名

使用 `SPEC-NNNN-short-title.md`。每篇只覆盖一个业务能力或边界明确的技术改动。

## 固定流程

1. 复制 [spec-template.md](spec-template.md)。
2. 分开记录当前事实、目标行为与假设。
3. 链接相关 ADR、API/schema contract 与测试。
4. 在实现前写清 Acceptance Criteria。
5. 实现后记录验证证据并更新状态。

认证机制、数据存储所有权、公开兼容性、部署拓扑等跨模块且难逆转的选择，除 Spec 外还必须有 ADR。
