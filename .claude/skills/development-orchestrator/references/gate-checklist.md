# 分层门禁清单

> fan-in 后按序执行,任一层不过则回路。因两端 implementer 都是 **sonnet**,门禁是**质量承重墙**——第 ③ 层(opus 安全+集成定向复核)**强制,非可选**。

## ①硬测试门(不绿不放行)

- **后端**:`cd backend && ./gradlew test`
  - 需 Docker/testcontainers(自起 PG+Redis)。本机无 Java → 走 **DooD 容器**:挂 **repo 根**(非 `backend`)+ `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + 关 Ryuk + 挂 docker socket;否则 redis 端口等待 / initdb 路径失败。
  - 失败真因藏在 `backend/build/test-results/**/*.xml`,别只看 stdout。
- **前端**:`cd frontend && npm run lint && npm run build`
  - 本机 node v24 在 PATH 可原生跑;回退 `node:20` 容器(DooD)。提交前**还原 `next-env.d.ts`**。React 19 lint 坑注意。
- **改了 `db/initdb/` seed → 必须** `cd backend && ./gradlew cleanTest test`
  - seed 整目录即 testcontainer 集成测试 fixture(`BaseIntegrationTest` 用 `withCopyFileToContainer`),但不在 `test` task 输入,不 cleanTest 会被判 UP-TO-DATE 不重跑。

## ②`/code-review`

- 对合并后的 diff 做**通用正确性 / 复用 / 坏味**审查。
- 覆盖两侧变更(cherry-pick 到 develop 后的整体 diff)。

## ③定向复核(强制,因两端 sonnet)

- **`security-analyst`(opus)** —— sonnet 后端的安全承重墙:
  - 认证/授权:`@PreAuthorize` 是否覆盖全部写操作、标在 service 方法。
  - 多租户隔离:查询是否在 **JPQL/SQL/Cypher 层按 `kindergarten_id` 过滤**(而非加载后过滤);跨租户 → 404。
  - PII/密钥:RRN 仅 HMAC+pepper 存储、不打日志;摄像头流 AES 密钥环境化;secret `${ENV}` fail-fast;Neo4j 不投影 PII。
  - 注入/CSRF:拼接 SQL/Cypher、未校验上传;CSRF 仅 `/api/v1/internal/**` 豁免;internal Bearer 范围最小。
- **`integration-analyst`(opus)** —— 契约双侧吻合:
  - 后端 DTO/VO ↔ 前端 `services/apis/*.api.ts` **逐字段**比对(字段名/可空性/enum/嵌套/分页 `Page`↔`PageResponse`)。
  - SSE/事件协议对齐(心跳/Last-Event-ID/replay 上限;detection→notification 事件链闭合)。
  - enum 三处同步(DB / 后端 `type.*` / 前端 i18n)。

## ④自修回路

- findings 回给**对应 implementer**(后端问题 → backend-implementer;契约错位 → 错位侧)自修 → 重跑 ①–③ **直到清零**。
- **high-risk 一律 halt 等维护者批准**(sub-skill:implement-review-loop 的 halt 约定;批准后编辑 run 脚本 halt 块 + 带**同样 args** resume 放行)。破坏性变更(schema/迁移/删除)同样须维护者逐个批准。
- 回路 **exhausted 仍未清零** → dev-lead 自验 + 提交剩余修正(记忆:loop exhausted 末轮 fix 常留工作树**未提交**,Lead 须自验+提交),并在收口报告**如实标注**未清零项,不掩盖。

## 通过判据

| 层 | pass 条件 | 不过时去向 |
|---|---|---|
| ①硬测试门 | 后端 test 绿 + 前端 lint&build 绿(改 seed 则 cleanTest 绿) | 回对应 implementer 修 |
| ②/code-review | 无阻塞级正确性/复用问题 | 回对应 implementer 修 |
| ③安全复核 | security-analyst 无 confirmed critical/high | 回 backend-implementer(越权/PII);high-risk → halt 等批准 |
| ③集成复核 | integration-analyst 无契约错位 | 回错位侧;契约本身含糊 → 回 design |
| ④自修回路 | ①–③ 全清零 | exhausted → dev-lead 自验+提交+标注未清零 |

> **DooD 测试门耗时**:后端全套件在本机走容器较慢;默认跑全套保正确性,可按 change 影响域跑子集(但子集通过后收口前仍须至少一次全套件绿)。
