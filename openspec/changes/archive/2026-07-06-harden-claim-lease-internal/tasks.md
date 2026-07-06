# Tasks — harden-claim-lease-internal (C2)

> 纯 backend 域，非破坏性（无 schema/迁移/删除面）。develop 直接提交。TDD。
> 覆盖：ARC-01 + SEC-05 + SEC-07 + PRF-09 + PRF-11。

## 1. ARC-01 — 抽 CameraStreamInternalService
- [x] 1.1 新建 `service/CameraStreamInternalService.java`，迁入 `getStreamCredential` / `listActiveStreamsForAi` / `claimStreams`（连同 `resolveActiveModelId` 私有辅助）
- [x] 1.2 `CameraStreamService` 只保留 4 个 tenant `@PreAuthorize` CRUD 方法 + `encryptPasswordIfPresent`/`requireSameKindergarten`
- [x] 1.3 改 controller（`StreamCredentialController` 及 claim/active-streams 的 controller）注入 `CameraStreamInternalService`
- [x] 1.4 保留原类头注释里关于「HTTP 层 hasRole(AI_SERVICE) 鉴权、无会话级 @PreAuthorize」的 rationale，迁到新类
- [x] 1.5 现有 claim/lease/credential 测试全绿（迁移不改行为）

## 2. SEC-05 — 补审计（S1_EVIDENCE_READ）
- [x] 2.1 凭据解密成功后写 `SecurityAuditWriter.record(S1_EVIDENCE_READ, {deploymentId, streamId, outcome})`；**不含明文凭据/token**
- [x] 2.2 claim 认领（新领到的 streamId）写审计条目
- [x] 2.3 审计写入不占用 claim 热路径连接/时延特征（参照既有 audit 写法：短事务/异步，勿在事务内跨外部 IO）
- [x] 2.4 测试：断言凭据读与 claim 各触发一条 S1_EVIDENCE_READ；断言审计 payload 不含明文密码/token

## 3. SEC-07 — capacity 上界
- [x] 3.1 `StreamClaimRequest.capacity` 加 `@Max`（可辩护上界；常量或 `@Value`），更新 javadoc
- [x] 3.2 测试：超上界 capacity → 400（`@Valid` 触发）；边界值通过

## 4. PRF-09 — Redis 批量化（保原子性）
- [x] 4.1 `StreamLeaseService` 加批量 renew（多键 compare-and-renew，pipeline 或多键 Lua）+ 批量 tryClaim（多键 SET NX），**每键原子语义不变**
- [x] 4.2 `claimStreams` 改用批量 API，减少 O(capacity+N) 逐键往返为 O(1)~O(批) 往返
- [x] 4.3 测试：并发认领竞态仍「每流至多一栈胜出」；续租不误刷他栈租约；批量与逐条结果等价

## 5. PRF-11 — Redis 出事务边界
- [x] 5.1 `claimStreams`：DB 投影读用短事务取回后关闭；Redis 批量操作在事务外
- [x] 5.2 确认无 `@Transactional(readOnly)` 跨 Redis 循环持连接
- [x] 5.3 测试：行为等价（assigned 结果不变）

## 6. 门禁
- [x] 6.1 `cd backend && ./gradlew test`（本机 Java 21 原生跑，非 DooD）全绿（348 tests, 0 failures/errors）
- [x] 6.2 无 seed 改动（不需 cleanTest）
