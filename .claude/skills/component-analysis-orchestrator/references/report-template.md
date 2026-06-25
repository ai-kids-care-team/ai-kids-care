# 综合报告模板（lead 产出 `_workspace/00_analysis_report.md`）

```markdown
# AI Kids Care 多角度组件分析报告
日期：YYYY-MM-DD ｜ 档位：轻量/标准/深度 ｜ 角度：架构/质量/安全/集成/性能/用户 ｜ 团队：component-analysis-team

## 1. 执行摘要
- 一段话总评 + 最关键的 3 条结论（决策者只读这段也能拍板）。
- 统计：critical N / high N / medium N / low N（仅计 `verdict=confirmed`；refuted 见附录）。
- 验证说明：本次档位 = X；high+medium 已经 finding-verifier 复核（静态/含 DooD）。

## 2. 组件健康度评分
| 组件 | 架构 | 质量 | 安全 | 集成 | 性能 | 用户 | 综合 | 一句话 |
|------|------|------|------|------|------|------|------|--------|
| backend  | A–F | … | … | … | … | … | … | … |
| frontend | … |
| ai       | … |
| db       | … |
| infra    | … |
> 评分给字母等级 + 简短依据，不要只给数字。某组件不适用某角度（如 db 无"用户"角度）标 `—`。

## 3. 关键发现（按修复优先级排序）
> 每条带多角度佐证（cross_refs 合并后的视角）+ 验证结论。
### P1 — <title>  [severity｜component｜涉及角度｜验证: verdict/method]
- 位置 / 证据 / 影响 / 修复建议 / confidence / verification(verdict·method·votes·note)
### P2 …

## 4. 修复路线图
- **立即（本周）**：critical + 阻塞项。
- **近期**：high。
- **持续改进**：medium/low 主题化归并（如「补齐 tenant 隔离集成测试」）。

## 5. 覆盖与局限
- 已覆盖：组件 × 6 角度矩阵；本次档位与执行模式（fan-out / 团队）。
- 未动态坐实（`unverified` / unverified-dynamic）项及原因（如本机无 Java/Node、DooD 不可用 → 仅静态）。
- 存疑项（confidence=low 或 verdict=unverified）清单，建议人工确认。

## 附录：被反驳（refuted）项
> 不删除，保留出处 + 反驳理由，供追溯与防止下次重复误报。
- <id> [原 severity｜component] — 反驳理由（verification.note）
```

要点：执行摘要必须自包含；统计只计 confirmed；健康度评分给等级 + 依据；发现按「先修什么」排序而非按角度堆叠；refuted 入附录不入正文；局限节如实交代未动态坐实项，不掩盖未覆盖。
