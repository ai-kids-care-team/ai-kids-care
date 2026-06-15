---
type: assessment
date: YYYY-MM-DD
status: Current
baseline_commit: "<commit>"
scope: "<scope>"
---

# <Assessment 标题>

## 执行摘要（Executive Summary）

简明说明成熟度与主要风险。

## 范围与方法（Scope And Method）

- 检查范围。
- 已运行的 command / test。
- 未验证范围。

## 实现事实（As-Built Facts）

带证据的架构与业务流程事实。

## 发现（Findings）

| ID | Priority | Finding | Evidence | Impact |
| --- | --- | --- | --- | --- |
| `<id>` | `P0-P3` | `<fact>` | `<path/test>` | `<impact>` |

## 文档漂移（Documentation Drift）

| 文档 / 声明 | 可信度 | 冲突 | 必要动作 |
| --- | --- | --- | --- |
| `<claim>` | `High/Medium/Low` | `<evidence>` | `<action>` |

## 建议（Recommendations）

按优先级排列，并与观察事实分开。

## 开放问题（Open Questions）

- 问题与负责人。

## 验证记录（Verification Record）

| 检查 | 结果 | 限制 |
| --- | --- | --- |
| `<command>` | `<pass/fail/blocked>` | `<detail>` |
