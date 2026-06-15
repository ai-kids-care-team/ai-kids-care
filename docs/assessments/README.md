# Assessments

Assessment 是某个 commit 上的日期化仓库观察。它必然会过期，不能悄悄变成“永远正确”的架构文档。

## 规则

- 文件名：`YYYY-MM-DD-short-topic.md`。
- 记录 commit、范围、证据、验证命令与限制。
- Findings 与 Recommendations 分开。
- 不通过修改旧报告来伪装“仍然最新”；应新增报告并链接前一版。
- 稳定事实进入 `docs/architecture/`；目标变化进入 Spec 或 ADR。

后续审计使用 [assessment-template.md](assessment-template.md)。

## 索引

| 日期 | Assessment | Baseline |
| --- | --- | --- |
| 2026-06-10 | [代码与文档审计](2026-06-10-codebase-audit.md) | `ead603e` |
