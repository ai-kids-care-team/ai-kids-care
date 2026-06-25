---
name: analyze-architecture
description: 从架构/结构角度审查组件——分层、依赖方向、耦合内聚、设计模式一致性、可扩展性与性能隐患。architecture-analyst 使用。当需要架构审查、结构评估、依赖分析、识别上帝类/循环依赖/性能瓶颈时使用。
---

# analyze-architecture — 架构角度审查方法

从**结构**视角审查，回答「组织得好不好、依赖往哪流、能否扩展」。每条结论落 `file:line`，按 `component-analysis-orchestrator` 的 finding schema 输出（id 前缀 `ARC-`）。

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

### 可扩展性 / 性能隐患（重点）
- **N+1 查询**：JPA 关联是否 LAZY 滥用、循环里查库。
- **同步阻塞**：通知发送、AI ingest、SSE 是否阻塞请求线程；@Async 懒代理坑（参考 getPhone 案例）。
- **无界重试 / 背压**：ingest 重试是否有界；SSE replay 上限；事件消费积压。
- **单点 / 多实例**：定时任务（DeferredNotificationScanner）多实例是否会重复执行（ShedLock 是否到位）。

## 手法
- 用 Grep 找超长文件、`@Transactional` 范围、`for` 循环内的 repository 调用、`@Scheduled`、`@Async`。
- 读 `application.yml`、`docker-compose*.yml` 理解拓扑与连接边界。
- 本机无 Java/构建工具 → 静态阅读即可，标 confidence=medium。

## 协作
跨边界的架构问题（component=cross）→ SendMessage 抄送 `integration-analyst`。涉及数据访问性能且想知有无测试 → 问 `quality-analyst`。完成写 `_workspace/architecture_findings.md` 并通知 lead。
