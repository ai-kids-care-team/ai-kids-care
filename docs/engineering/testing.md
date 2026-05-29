# 测试（Testing）

## 现状（已确认）

> ⚠️ **当前项目没有自动化测试基线。**

| 组件 | 测试现状 | 证据 |
| --- | --- | --- |
| 后端 | ✅ **无测试**：`backend/src/test/` 为空（仅 `build.gradle` 声明了 `spring-boot-starter-test`/`spring-security-test` 依赖，但无用例） | `backend/src/test` 检索为空 |
| 前端 | ✅ **无测试框架/脚本**：`package.json` 仅有 `dev/build/start/lint` | `frontend/package.json` |
| AI | ✅ **无测试目录**：有 `examples/` 与 `realtime_persistence_demo.py` 作为演示，非自动化测试 | `ai/` 检索 |

`./gradlew test` 可执行但无用例。

## `CLAUDE.md` 的测试规则（修改代码时必须遵循）

> 修改代码时：
> 1. 识别现有测试。
> 2. 除非明确要求变更，否则保留既有行为。
> 3. 必要时为遗留行为补充**特征化测试（characterization tests）**。
> 4. 新增或更新自动化测试。
> 5. 验证受影响的工作流。
> 
> 不得在无说明理由的情况下删除测试。

## 对当前状态的影响（事实陈述，非方案）

- 由于无测试基线，任何改动都缺少回归保护——`CLAUDE.md` 要求的"识别现有测试"会得到"无"的结果。
- 修改遗留逻辑前，"补充特征化测试"在此尤为关键（用于固定当前行为，再安全改动）。
- 验证只能依赖**手工验证关键工作流**（见 [operations/runbook.md](../operations/runbook.md) 与 [local-development.md](local-development.md)）。

## 待确认

> ❓ 是否存在仓库之外的测试（如手工测试用例、Postman 集合、独立 QA 流程）？测试策略（单元/集成/E2E 的取舍）尚无记录，列入 [open-questions](../modernization/open-questions.md)。
