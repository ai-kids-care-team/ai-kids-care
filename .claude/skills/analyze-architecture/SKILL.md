---
name: analyze-architecture
description: 从架构/结构角度审查组件——分层、依赖方向、耦合内聚、设计模式一致性。architecture-analyst 使用。当需要架构审查、结构评估、依赖分析、识别上帝类/循环依赖时使用。（性能/扩展性已独立为 analyze-performance 角度。）
---

# analyze-architecture — 架构角度审查方法

从**结构**视角审查，回答「组织得好不好、依赖往哪流、模式是否一致」。每条结论落 `file:line`，按 `component-analysis-orchestrator` 的 finding schema 输出（id 前缀 `ARC-`）。
> 运行时性能与可扩展性**已独立为 `analyze-performance` 角度**。本角度只看纯结构；发现运行时性能/扩展隐患标 `component` 并 SendMessage 抄送 `performance-analyst`，靠 cross_refs 互链、不重复定级。

## 为何这样审（原则）
架构问题的代价是**未来改动的昂贵程度**——它不立刻报错，但会让每次迭代变慢、让 bug 更易引入。所以重点找「现在能跑、但改起来会痛」的结构。

## 检查清单（按本工程栈）

### 分层与职责
- Spring：controller 是否只做编排，业务逻辑在 service？有无 controller 直接碰 repository、或 service 里塞 HTTP 细节？
- 找**上帝类**：行数异常多的 service（如 NotificationService / DetectionEventService 若包揽过多职责）。
- 前端：页面组件是否混入数据获取+业务逻辑+渲染？RTK Query api 层与 UI 是否分离。
- AI：serving / inference / training / utils 边界是否清晰，推理与训练是否纠缠。

### 依赖方向与耦合
- 包/模块依赖是否单向无环；高层是否依赖抽象而非具体。
- 跨组件隐式契约（backend 与 ai 对 detection 表的写权责：ADR-0026 规定 backend 是唯一写者——验证 ai 确实只走 internal API 不直连库）。
- PostgreSQL ↔ Neo4j 一致性边界：是否存在跨两库的事务期望（不应有）。

### 设计模式一致性
- 同类问题同一模式：通知渠道是否统一适配器（Pushover/SMS）？事件驱动是否一致用 Spring events？
- 有无重复造轮子 / 同一概念多种实现。

### 边界与一致性（结构层面）
- API 版本化策略是否一致（`/api/v1/**` vs 遗留 `/api/**`）。
- 死代码 / 半成品扩展点（如 `denyAll()` 守卫却全量部署的服务）从**结构**角度是否该门控或下线。

## 手法
- 用 Grep 找超长文件、跨层调用、重复实现、`@PreAuthorize("denyAll()")` 类死扩展点。
- 读 `application.yml`、`docker-compose*.yml` 理解模块边界与依赖方向（拓扑的**性能**含义交给 performance 角度）。
- 本机无 Java/构建工具 → 静态阅读即可，标 confidence=medium。

## 协作
跨边界的架构问题（component=cross）→ SendMessage 抄送 `integration-analyst`。发现运行时性能/扩展隐患 → 抄送 `performance-analyst`（本角度不深挖）。完成写 `_workspace/architecture_findings.md` 并通知 lead。
