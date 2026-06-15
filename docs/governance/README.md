# 文档治理（Documentation Governance）

## 目的

仓库需要分开维护两类真相：

- **As-built truth**：当前 code、schema、configuration、test 与 runtime 实际做什么。
- **Intended truth**：Approved Spec 与 Accepted ADR 要求系统变成什么。

混用这两类真相，是当前文档漂移的主要来源。

## 信息架构

| Path | 负责内容 | 不应包含 |
| --- | --- | --- |
| `docs/product/` | 稳定产品术语、角色、结果 | 实现状态声明 |
| `docs/specs/` | Proposed / Approved 行为与验收标准 | 更适合 ADR 的历史理由 |
| `docs/architecture/` | 经验证的当前组件边界与数据流 | 未明确标注的未来架构 |
| `docs/decisions/adr/` | 长期决策与 tradeoff | 长任务清单、每日进度 |
| `docs/api/` | Contract 导航与跨服务语义 | 可由 OpenAPI 生成的手抄字段 |
| `docs/engineering/` | 开发工作流与约定 | 产品需求 |
| `docs/operations/` | 部署、配置、runbook | Design proposal |
| `docs/assessments/` | 日期化审计 | 永久有效的架构声明 |
| `docs/modernization/` | Live backlog、open questions、roadmap | 新增审计附录 |

## 存储规则

1. 组件本地 setup 放在组件旁（`frontend/README.md`、`ai/README.md`）。
2. 跨组件行为放在 `docs/`。
3. 生成型 contract 必须声明 generator 与 verification command。
4. 持续追加的文档必须有 index 和 template。
5. 日期敏感审计是不可变快照；新审计创建新文件。
6. 决策改变时不重写 Accepted ADR；新增 superseding ADR。
7. Status 必须区分 decision state 与 implementation state。

## 固定模板

- Feature / Technical Spec：[`docs/specs/spec-template.md`](../specs/spec-template.md)
- Architecture Decision：[`docs/decisions/adr/adr-template.md`](../decisions/adr/adr-template.md)
- Technical Assessment：[`docs/assessments/assessment-template.md`](../assessments/assessment-template.md)

## 漂移控制

以下检查应逐步成为 CI gate：

- 生成并 diff backend OpenAPI。
- 用 OpenAPI 校验 frontend API path 与 payload。
- 校验 DBML、Flyway migration、demo schema 与 JPA mapping。
- 检查 Markdown 内部链接与必填 frontmatter。
- `Implemented` Spec 缺少验证证据时失败。
- ADR 把实施状态写进 decision status 时失败。

## 迁移方向

不要立即搬动全部旧文件。新工作先使用新结构，旧文档在被触及时迁移：

1. 新工作从 `docs/specs/` 开始。
2. 新审计进入 `docs/assessments/`。
3. `docs/modernization/current-state-assessment.md` 保留为历史快照。
4. 既有 ADR 保留 ID 与历史，逐步规范 metadata。
5. 先用生成 contract 替代手工 API 字段目录，再删除旧 prose。
