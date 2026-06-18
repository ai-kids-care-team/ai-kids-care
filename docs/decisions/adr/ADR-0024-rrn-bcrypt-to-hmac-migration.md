---
ADR: ADR-0024
title: "ADR-0024: RRN 由 BCrypt 迁移到 HMAC-SHA-256 + pepper 的实施步骤"
status: Accepted
implementation: Phase 3 Complete (D1–D6 + V5/V6 + entity/service/seed/DBML cleanup, commit 7ceb3da)
date: 2026-06-17
deciders: 接手人（Lead）起草；维护者 2026-06-18 Accept（PII / schema / 迁移属高风险）
supersedes: []
superseded_by: null
related_specs: [SPEC-0001]
---

# ADR-0024: RRN 由 BCrypt 迁移到 HMAC-SHA-256 + pepper 的实施步骤

> 本 ADR 是 [ADR-0010](ADR-0010-rrn-one-way-hash.md) 末尾点名要求的「Implementation 子 ADR」。ADR-0010 已**决定**算法（HMAC-SHA-256 + pepper）并否决任何可逆加密；本 ADR 不重开该决策，只**固化迁移落地步骤**：Flyway 迁移、代码改造、种子重生成、存量回填与删旧列**硬门**。
> **范围边界**：本 ADR 使用**单一 pepper**。pepper 的**轮换机制**（密钥版本化、退役旧 pepper）是独立的横切关注点，拆到 [ADR-0025](ADR-0025-rrn-pepper-rotation.md)，建立在本 ADR 之上。

## 状态（Status）

Decision: `Accepted`（维护者 2026-06-18 签署）

Implementation: `Phase 3 Complete`（D1–D6 已落地 commit 8a15f99；V5/V6 + entity/service/seed/DBML cleanup 已落地 commit 7ceb3da，branch task/adr-0024-phase3；D7 RRN 再校验流程不在本任务范围，见 Open Questions）

> **更新（2026-06-18）：** D7 的「BCrypt → HMAC 回填」前提已 **OBE**——V5/V6 已删 `rrn_encrypted`、三表 `rrn_hash` NOT NULL、无 BCrypt 存量与读路径（`ChildrenService` 纯 HMAC）。D7 剩余价值（持有明文时机重算 RRN hash）并入 [ADR-0025](ADR-0025-rrn-pepper-rotation.md) R4，随 pepper 轮换 **按需实施**（已推迟，见 ADR-0025 状态注）。

> 高风险（PII + schema/migration + 注册认证路径）。Accept 后用 `implement-review-loop` 单 lane 串行落地——迁移、读/写路径、种子是强耦合、不可并行的原子改动。建议分阶段：阶段一 = V4 + 读写 + 种子 + 单 pepper 配置；阶段二 = RRN 再校验流程 + 回填；阶段三 = 达硬门后 V5/V6。

## 维护者已决（2026-06-17）

1. **删列门控 = 硬性**：`rrn_hash IS NULL` 全为 0 才可 V5/V6（不接受任何残留）。
2. **pepper 必须可轮换**，但轮换机制**拆为独立 [ADR-0025](ADR-0025-rrn-pepper-rotation.md)**，不并入本迁移 ADR。
3. **CameraStream AES-GCM 链路**划在范围外，另起 **ADR-0026**。

## 背景（Context，as-built 已核验 @ 2026-06-17 / commit cd45606）

- **存储**：`children` / `guardians` / `teachers` 三表均为 `rrn_first6`（明文，检索用）+ `rrn_encrypted`（列名误导，实为 BCrypt 单向哈希）。见 `docs/engineering/schema-digest.md`。
- **写入**（仅 Guardian / Teacher 自注册时写本人 RRN）：`AuthService.java:131`、`:184` 用 `passwordEncoder.encode(request.getRrnBack7())`。**现状只哈希后 7 位**，不含 `rrn_first6`。
- **按 RRN 查找**（仅儿童，注册时定位儿童）：`ChildrenService.getChildEntityByRRN`（`:115-119`）= `findByRrnFirst6` 候选集 → 逐条 `matches()`（O(N)×BCrypt）；被 `AuthService.java:112`、`:232` 调用。
- **儿童 RRN 无 JPA 写入路径**：由种子/管理员入库（`db/initdb/26_children_seed.sql`）。
- **Flyway**：仅 V1/V2/V3，无 V4，无 `rrn_hash`。
- **pepper 尚不存在**；`AesGcmCryptoUtil` 仅用于摄像头流凭证，**不得**用于 RRN。

## 决策（Decision）

### D1. 哈希规格（RrnHashUtil）

- 新建 `backend/.../security/RrnHashUtil`（**不**复用 `AesGcmCryptoUtil`——HMAC 是单向 PRF，语义不同）。
- `hash(pepper, first6, back7)` = `Base64URL-nopad( HMAC-SHA-256(key = pepper(UTF-8), msg = (first6 + back7)(UTF-8)) )`。
- **输入为完整 13 位（first6 ‖ back7）**——与现状（仅 back7）不同，目的是让唯一约束作用于完整 RRN。读写两端拼接顺序必须一致。

### D2. 写路径（单写）

- `AuthService.java:131`、`:184`：`encode(getRrnBack7())` → `RrnHashUtil.hash(pepper, first6, back7)`，写 `rrn_hash`。
- 新行只写 `rrn_hash`，不再写 `rrn_encrypted`（故 V4 须放松其 NOT NULL，见 D4）。

### D3. 读 / 校验路径（双读 + 懒回填）

- `ChildrenService.getChildEntityByRRN` 改为：
  1. 先算 `h = RrnHashUtil.hash(pepper, first6, last7)`，`repository.findByRrnHash(h)` 一次等值命中。
  2. 未命中（存量 BCrypt 行 `rrn_hash IS NULL`）→ 回退原 `findByRrnFirst6 + matches`；命中后**同事务内回填** `rrn_hash = h`（懒回填；此刻持有明文）。
- `ChildRepository` 新增 `Optional<Child> findByRrnHash(String rrnHash)`。
- 过渡期保留 `PasswordEncoder` 注入（供回退）；V6 后删除。

### D4. Flyway 迁移序列（三步，跨任务/受硬门约束）

- **V4__rrn_hash_add**（阶段一）：三表 `ADD COLUMN rrn_hash varchar`（可空）；`CREATE UNIQUE INDEX` on `rrn_hash`（PostgreSQL 唯一索引对多 NULL 互不冲突，存量 NULL 安全——脚本注释写明）；`ALTER COLUMN rrn_encrypted DROP NOT NULL`；列注释 `암호문(암호화 저장)` → `단방향 해시(검증용, 복호화 불가)`。
- **V5__rrn_hash_enforce**（**达硬门后**，单独任务）：`rrn_hash SET NOT NULL`；唯一索引升级为 `UNIQUE` 约束（防重复入园，ADR-0010 §95）。
- **V6__rrn_encrypted_drop**（V5 后，单独任务）：`DROP COLUMN rrn_encrypted`；删 D3 的 BCrypt 回退分支与 `ChildrenService` 的 `PasswordEncoder` 注入。
- 每次迁移后 `bash scripts/schema-digest.sh` 重生成（否则 `schema-digest-drift.yml` 红）。
- **前向兼容**：本 ADR 不建 `rrn_hash_key_version` 列；ADR-0025 实现轮换时再添加该列并把存量行标注为初始版本（纯标注 UPDATE，不需明文）。

### D5. pepper 配置（单一，复用 ADR-0009 密钥路径）

- `application.yml` 新增 `rrn.hash.pepper: ${RRN_HASH_PEPPER}`（**裸引用、无默认值**，fail-fast，与 N-1 一致）。
- **下游调用方一并更新**（吸取 Compose Config 变红教训）：`docker-compose.yml` / `docker-compose.prod.yml` 后端 `environment` 增 `RRN_HASH_PEPPER`（必填 `${RRN_HASH_PEPPER:?...}`）；`.env.example` 增槽；`backend/src/test/resources/application-test.yml` 设**固定非密 test pepper**；`.github/workflows/compose-config.yml` 的 dummy `env` 增 `RRN_HASH_PEPPER`。
- pepper 与 DB 备份/快照**分库存放**（ADR-0010 §89 的安全前提）。

### D6. 种子重生成（dev/test）

- `db/initdb/{26_children_seed,29_guardians_seed,30_teachers_seed}.sql` + `01_create_schema.sql`（列名/注释）：用各 seed 行注释中的原始 RRN + **test pepper** 重算 `rrn_hash`，替换 BCrypt 值。
- DBML（`db/dbml/schema.dbml`）同步；ERD `.mmd` 由 schema 重新派生（不手工编辑）——清 ADR-0010 §131 遗留勘误。

### D7. RRN 再校验流程（硬门的前提）

- **儿童**：注册流调用 `getChildEntityByRRN` 时持有完整 RRN → D3 读路径**自动懒回填**。
- **保护者 / 教师本人 RRN**：**无 read-by-rrn 触发点**（只写不查）→ 必须新增 **RRN 再校验流程**：用户下次认证后提供完整 RRN → 经 BCrypt 校验通过 → 用 `RrnHashUtil` 重算写回 `rrn_hash`。UX 细节（触发时机、强制 vs 可跳过、失败处理）另立小 spec（见 Open Questions）。
- **demo（合成种子）**：D6 直接将种子置为 HMAC、无 BCrypt legacy 行 → **跳过再校验**，硬门即时满足。真实生产存量才需要这条流程。
- 该再校验流程在 ADR-0025 会被**复用**于 pepper 轮换的重算。

## 删列门控（硬性，维护者决定）

- **V5/V6** 的前置硬门：`SELECT count(*) FROM children/guardians/teachers WHERE rrn_hash IS NULL` **= 0**（BCrypt legacy 行）。
- **不接受任何残留**。真实生产存量必须先经 D7 再校验流程全部迁完、计数归 0 才能推进。
- 该硬门由一个可复跑的核对查询/脚本支撑（建议纳入运维 runbook 或一次性校验测试）。

## 验证（Compliance）

```
bash scripts/test-backend.sh --compile                              # 类型/MapStruct
bash scripts/test-backend.sh '*RrnHash*' '*AuthService*' '*Children*' '*Auth*'
bash scripts/test-backend.sh '*Sensitive*' '*PublishedOpenApi*'     # rrn_hash 不得泄漏到 response/OpenAPI
bash scripts/schema-digest.sh                                       # 迁移后重生成
```
- 新增测试：`RrnHashUtil` 单测（已知向量、空 pepper fail）；HMAC 命中 / BCrypt 回退 + 懒回填；唯一约束冲突（重复 RRN）；硬门核对查询。
- test profile 必须注入固定 pepper（否则 Spring 上下文 fail-fast 起不来——N-1 同源风险）。

## 后果（Consequences）

- **正面**：消除 O(N)×BCrypt 退化与「最坏日尖峰」；`UNIQUE(rrn_hash)` 把「同一 RRN 不重复入园/绑定」变 DB 强约束；语义/命名/实现/文档四方一致，清账 `rrn_encrypted` 误述。
- **负面 / 代价**：
  - 引入 pepper 密钥管理（env→KMS）。
  - **必须新增 RRN 再校验流程**（产品/UX 工作量）——这是把门控设为「硬性」的直接代价；保护者/教师存量行只能经此回填。
  - 迁移分三个 Flyway 步、跨任务，需协调。
- **影响范围**：三表 schema、`AuthService`、`ChildrenService`、`ChildRepository`、`RrnHashUtil`(新)、再校验流程(新)、`application.yml`/compose/`.env.example`/`application-test.yml`/`compose-config.yml`、`db/initdb` 种子 + DBML + ERD。

## 风险与回滚

- **pepper 丢失** = 所有 RRN 校验失效（最高风险）：pepper 与 DB 分离备份，纳入密钥管理；轮换能力见 ADR-0025。
- V4 只增列/放松约束，**可回滚**（旧 BCrypt 路径仍工作）。V6 删列**不可逆** → 受硬门 + 维护者签署双重保护。
- 唯一约束在 `rrn_hash IS NULL` 期间靠「NULL 互不冲突」语义安全；V5 升 NOT NULL 前由硬门保证无 NULL。

## 考虑过的备选（Alternatives Considered）

- 维持 BCrypt / 可逆加密 / 混合方案：ADR-0010 已否决，不重开。
- **一次性启动批量重算**：不可行——BCrypt 不可逆，无法从存量哈希反推明文；只能经持有明文的时刻（懒回填/再校验）重算。
- **原地 rename `rrn_encrypted`→`rrn_hash`**：内容不兼容，采用「加列→回填→删列」，end-state 等价 ADR-0010 的改名要求。
- **把 pepper 轮换并入本 ADR**：维护者否决——轮换是独立横切关注点，拆到 ADR-0025（避免迁移 ADR 过载、且轮换可独立排期）。

## Open Questions（剩余，待后续）

1. **RRN 再校验流程的 UX spec**（D7 依赖）：触发时机（登录后强制 / 可延后）、失败处理、对未再校验用户的功能限制——建议另立小 spec，在阶段二实现前定稿。
2. **ADR-0025（pepper 轮换）** 与本 ADR 的实现先后：本 ADR 可先以单 pepper 落地；轮换能力按 ADR-0025 排期叠加。
3. **ADR-0026（CameraStream）** 何时起草：独立决断，不阻塞本 ADR。

## 关联（References）

- 母决策：[ADR-0010](ADR-0010-rrn-one-way-hash.md)。密钥路径：[ADR-0009](ADR-0009-restore-auth-enforcement.md)。发布/评审门：[ADR-0020](ADR-0020-branch-protection-release-model.md)。基线：`docs/assessments/2026-06-17-followup-audit.md` §N-8。
- 后续：[ADR-0025](ADR-0025-rrn-pepper-rotation.md)（pepper 轮换，建立在本 ADR 之上）、ADR-0026（CameraStream，待起草）。
- 代码：`AuthService.java:131,184`（写）、`:112,232`（查）、`ChildrenService.java:115-119`、`ChildRepository.findByRrnFirst6`。
- schema/种子：`db/initdb/{01_create_schema,26_children_seed,29_guardians_seed,30_teachers_seed}.sql`、`db/dbml/schema.dbml`、`docs/engineering/schema-digest.md`。
