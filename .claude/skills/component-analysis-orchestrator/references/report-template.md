# 综合报告模板（lead 产出 `_workspace/00_analysis_report.md`）

```markdown
# AI Kids Care 多角度组件分析报告
日期：YYYY-MM-DD ｜ 角度：架构/安全/质量/集成 ｜ 团队：component-analysis-team

## 1. 执行摘要
- 一段话总评 + 最关键的 3 条结论（决策者只读这段也能拍板）。
- 统计：critical N / high N / medium N / low N。

## 2. 组件健康度评分
| 组件 | 架构 | 安全 | 质量 | 集成 | 综合 | 一句话 |
|------|------|------|------|------|------|--------|
| backend  | A–F | … | … | … | … | … |
| frontend | … |
| ai       | … |
| db       | … |
| infra    | … |
> 评分给字母等级 + 简短依据，不要只给数字。

## 3. 关键发现（按修复优先级排序）
> 每条带多角度佐证（cross_refs 合并后的视角）。
### P1 — <title>  [severity｜component｜涉及角度]
- 位置 / 证据 / 影响 / 修复建议 / confidence
### P2 …

## 4. 修复路线图
- **立即（本周）**：critical + 阻塞项。
- **近期**：high。
- **持续改进**：medium/low 主题化归并（如「补齐 tenant 隔离集成测试」）。

## 5. 覆盖与局限
- 已覆盖：组件 × 角度矩阵。
- 未动态验证 / 跳过项及原因（如本机无 Java，backend 测试仅静态评估）。
- 存疑项（confidence=low）清单，建议人工确认。
```

要点：执行摘要必须自包含；健康度评分给等级 + 依据；发现按「先修什么」排序而非按角度堆叠；局限节如实交代，不掩盖未覆盖。
