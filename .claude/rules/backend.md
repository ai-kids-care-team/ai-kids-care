---
globs: backend/**
disclosure: path-scoped
---

# Backend 实现约定（`backend/`）

**Backend 包结构按层（非按功能）平铺**，根包 `com.ai_kids_care.v1`：
`controller`（仅路由分发，只注入 service）→ `service`（业务逻辑 + `@PreAuthorize` + `@Transactional`）→ `repository`（JPA；`GraphRepository` 是唯一手写 Cypher）+ `mapper`（MapStruct，entity↔VO/DTO）。另有 `entity / dto / vo / event / internal / security / type / bootstrap / config`。所有功能域混在同层，无 feature module。

- **命名**：输入 `XxxCreateDTO`/`XxxUpdateDTO`，持久化 `Xxx`(entity)，响应 `XxxVO`。MapStruct `unmappedTargetPolicy=ERROR`；Update 用 `NullValuePropertyMappingStrategy.IGNORE` 实现 PATCH。
- **JPA**：`ddl-auto: validate`、`open-in-view: false`。
- **方法级授权**：`@PreAuthorize("@authorizationPolicy.isAllowed(...)")` 标在 **service** 方法上（非 controller）。

> 多租户隔离约束与安全 invariants 见 `security.md`（其 glob 含 `backend/**`，写 backend 时必现）。

## 两类 Spring 事件，用法不同（新增 event 须遵循分类）

- `DetectionEventIngestedEvent` → `@Async @EventListener`：ingest 是 autocommit 无事务，**不能**用 TransactionalEventListener。
- `EventReviewedEvent`（record，payload 在 review 事务内 eager 预载关联）→ `@Async @TransactionalEventListener(AFTER_COMMIT)`：防异步线程无 persistence session 时懒加载失败。

## 异步与性能约束

所有 `@Async` 共用 Boot `applicationTaskExecutor`（core=8/max=16/queue=200 有界/CallerRunsPolicy，`application.yml`）；独立吞吐场景须声明命名 Executor + `@Async("bean")`。外部 HTTP（Pushover/SMS）**必须在事务边界外并设超时**（5s）；通知投递用 `REQUIRES_NEW` 把 DB 写拆成短事务、provider 调用夹在中间不占连接。AFTER_COMMIT 异步监听器里**禁用懒代理**，用 `findAllById` 批量预载。

## SSE 服务端实现

注册表是**进程内** `ConcurrentHashMap<kindergartenId, Set<SseEmitter>>` → **单实例假设**，多实例 fanout（Redis pub/sub）是 follow-up。

> SSE 线协议（端点、事件名、心跳/寿命/replay、`EventSource`）见 `contracts.md`。

## 测试命令与隔离环境

```bash
# Backend：测试需 Docker（testcontainers 自起 PG+Redis）
cd backend && ./gradlew test --no-daemon --stacktrace
cd backend && ./gradlew bootJar          # 构建 fat jar
```

本机有 Java 21 可原生跑；也可走 DooD 干净环境：挂仓库根(非 backend) + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`。
