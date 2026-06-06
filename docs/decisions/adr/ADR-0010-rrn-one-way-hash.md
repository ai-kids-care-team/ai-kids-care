---
ADR: ADR-0010
title: "ADR-0010: 主民登录号（RRN）采用单向哈希"
status: Accepted
date: 2026-05-29
deciders: 维护者（2026-05-29 Accept；HMAC-SHA-256 + pepper 子方案已选定）
---

# ADR-0010: 主民登录号（RRN）采用单向哈希

> **前瞻提案**。方向已由维护者于 2026-05-29 确认（OQ-SEC-4）：RRN **不可逆**——即只做**单向哈希**，**不**使用任何形式的可逆加密。本 ADR 形式化该决策，并界定随之需要决断的"哈希算法子选项"与必须随之执行的"文档/注释勘误"。

## 状态（Status）

Accepted（2026-05-29 签署；哈希算法选定 **HMAC-SHA-256 + pepper**；文档勘误已在 Design 期完成，schema/ERD 勘误与数据迁移留 Implementation）

## 背景（Context）

✅ RRN（주민등록번호）采用**拆分存储**：`rrn_first6`（前 6 位/出生日期，**明文**，用于检索/查重）+ `rrn_encrypted`（后位）。schema 列注释见 `01_create_schema.sql:683-685`（children）、`:729-731`（teachers）、`:845-847`（guardians）。
✅ 代码实际用 `passwordEncoder.encode()`（**BCrypt 单向哈希**）写入 `rrn_encrypted`：`AuthService.java:189`（guardian）、`:245`（teacher）。
✅ 仓库另有可逆的 `AesGcmCryptoUtil`（AES-256-GCM），但仅用于摄像头流凭证，**未用于 RRN**。

✅ **历史矛盾的来源（已澄清，2026-05-29）**：列名 `rrn_encrypted` + schema 注释"암호문(암호화 저장)"（密文/加密存储）以及由其派生到知识库的多处表述，**均为错误/误导性**——它们不反映系统的设计意图，也与实际代码不符。本 ADR 之后，凡仍把 RRN 描述为"可逆加密 / 암호문 / 可解密"的文字一律视为待勘误。

✅ **按 RRN 查找的业务需求确认存在（2026-05-29，维护者）**。当前实现路径：
- 入口：`ChildrenService.getChildEntityByRRN(rrnFirst6, rrnBack7)`，被 `AuthService.registerGuardian`（`AuthService.java:183`）调用，用于保护者注册时定位儿童。
- 算法：先按 `rrn_first6` 取候选集（同生日 + 同性别相关位），再对每条候选 `passwordEncoder.matches(providedBack7, candidate.rrn_encrypted)`。
- 现状评价（维护者）："可以完成查找逻辑，但并不确定是否最优"。本 ADR §"哈希算法子选项评估"对此作出回应。

## 决策（Decision）

1. **强决策**：RRN 后位**仅以单向方式存储，不可逆**。**任何**针对 RRN 引入可逆加密（AES/GCM 或其他）的方案均不予采纳。
2. **伴生勘误（必须在 Implementation 一并执行，不可拆分）**：
   - **Schema/ERD（属代码源）**：将 `rrn_encrypted` 列改名为 `rrn_hash`；schema 列注释由"암호문(암호화 저장)" → "단방향 해시(검증용, 복호화 불가)"；DBML 同步更新；ERD `.mmd` 由 schema 重新派生。
   - **文档勘误清单**：见"关联"节列出的具体文件/行（Design 期已完成）。
3. **哈希算法**：选定 **(b) HMAC-SHA-256 + pepper**（2026-05-29 维护者采纳，理由见下节评估）。pepper 与 [ADR-0009](ADR-0009-restore-auth-enforcement.md) 的 JWT secret 走**同一密钥管理路径**（环境变量 → 后续 KMS/Vault），不引入新的密钥管理范式；既有 BCrypt 数据的迁移路径留待 Implementation 子 ADR 固化。
4. `rrn_first6` 维持明文用于检索/查重——保留既有设计，并在隐私说明中明示其暴露出生日期的取舍。

## 哈希算法子选项评估（应维护者要求）

> 本节直接回应"现行方案是否最优"。结论后置；先把维度摆清。

### 候选方案

- **(a) 现状：BCrypt（逐记录加盐）+ `rrn_first6` 候选集 + 逐条 `matches()`**
  - 写入：`encode(back7)` 存 `rrn_encrypted`。
  - 查找：`WHERE rrn_first6=?` → 对每条 `matches(provided, hash)`。
- **(b) HMAC-SHA-256(pepper, full_rrn)，等值查找**
  - 写入：`hash = HMAC_SHA256(pepper, rrn_first6 + back7)` 存 `rrn_hash`，建唯一索引。
  - 查找：`WHERE rrn_hash = HMAC_SHA256(pepper, provided)`，一次命中。
- **(c) 混合：保留 BCrypt 作"验证列" + 新增 HMAC 作"查找列"**
  - 写入：两列同时写。
  - 查找：HMAC 等值定位；可选：再做 BCrypt 二次校验。
  - （列出以保完整性，下面会说明其在本场景过度工程）

### 评估维度

| 维度 | (a) BCrypt + 候选集 | (b) HMAC + pepper | (c) 混合 |
| --- | --- | --- | --- |
| **查找复杂度** | O(N) × BCrypt（N=同 `rrn_first6` 候选数；BCrypt strength=10 约 100ms/次） | O(1) 索引等值 | O(1) 索引 + 可选 O(1) BCrypt |
| **能否建唯一约束（防重复入园）** | ❌ 不能（每条 salt 不同） | ✅ 能（`UNIQUE(rrn_hash)`） | ✅ 能（在 HMAC 列上） |
| **DB-leak only（密钥未泄）抗暴力** | 🟢 强（BCrypt 慢） | 🟢 强（需 pepper 才能 brute force） | 🟢 强 |
| **DB-leak + pepper-leak** | 🟢 仍强（无 pepper 概念） | 🔴 弱（10^7 可能空间，秒级穷举） | 🟡 中（HMAC 列泄漏但 BCrypt 列还在） |
| **运维负担** | 🟢 无密钥管理 | 🟡 需 pepper 安全存储（env/KMS/Vault）+ 轮换策略 | 🔴 双列双写 + 双轮换 |
| **存储/写入成本** | 1 列、1 次 BCrypt | 1 列、1 次 HMAC（极快） | 2 列、2 次哈希 |
| **代码复杂度** | 🟢 现状 | 🟡 引入 pepper 注入 + 工具类 | 🔴 双路径 + 一致性 |
| **业务规模随用户增长的退化** | 🔴 候选集增大 → 注册延迟线性上升 | 🟢 不退化 | 🟢 不退化 |

### 量级带入（让数字说话）

- 现实参数：BCrypt strength=10 ≈ **~100ms/次**（Spring 默认；可调）。
- `rrn_first6` = 出生日期（YYMMDD），同日出生的儿童在系统中的候选数：
  - 单园 100 人 → 0–3 人 → 单次注册 0–300ms（**可接受**）。
  - 系统覆盖 100 园、共 10000 人 → 平均同日 ~3–8 人，**最坏**集中日（如月初/学年首日）可达 20–50 人 → 单次注册 2–5 秒（**临界**）。
  - 覆盖 1000 园、10 万人 → 同日 30–80 人 → 单次注册 3–8 秒（**不可接受**，且坐席体验差）。
- (b) HMAC：与 N 无关，单次 < 1ms + 一次索引查询。
- **注册是低频事件**这点缓解了 (a)，但**不缓解最坏日的尖峰**。

### 安全模型对比

两种方案在"**DB 单独泄露**"的最常见威胁下都强：
- (a) 攻击者拿到 BCrypt 哈希后，要逐条 brute force 10^7 空间，BCrypt 慢 → 实际不可行。
- (b) 攻击者无 pepper → HMAC 等价于带未知密钥的 PRF，无法离线 brute force。

差别出现在"**DB + pepper 同时泄露**"：
- (a) 没有 pepper 概念，不受此情形影响。
- (b) pepper 泄露后，10^7 可能空间秒级穷举完毕。
- 缓解：pepper 与 DB 物理/权限隔离（KMS/Vault/独立 secret 注入），与 JWT secret 走相同的密钥管理路径（[ADR-0009](ADR-0009-restore-auth-enforcement.md) 也将引入更严的 secret 管理）。
- 实操判断：**只要 pepper 不和 DB 备份/快照同库存放，两者同时泄露的概率显著低于"仅 DB 泄露"**。

### 评估结论 → 已采纳 (b)

✅ **2026-05-29 维护者采纳 (b) HMAC-SHA-256 + pepper**，理由按重要度排：

1. **可建唯一约束**——直接在 schema 上防止"同一 RRN 重复入园/重复绑定保护者"，这是 (a) 无法提供的**业务正确性**保障。
2. **查找复杂度恒定**——消除"最坏日尖峰"的退化曲线；从架构层面消掉一个未来必出问题的扩展性坑。
3. **逻辑更直观**——`WHERE rrn_hash = ?` 一次命中，移除候选集 + 逐条匹配的特殊路径。
4. **pepper 管理负担可控**——可与 JWT secret 同走环境变量/KMS 注入，新增一个 secret，不增加新的密钥管理范式。

不推荐 (a) 的核心原因不是"性能"，而是**"无法建唯一约束"导致业务正确性漏洞**——业务上"同一儿童不应被两次注册"这种不变量目前完全靠应用层 best-effort，DB 无强约束兜底。

不推荐 (c) 的原因：在本系统当前威胁模型下，混合方案的额外安全收益（应对"pepper 泄漏 + DB 泄漏"）需要双倍的写路径与一致性维护——代价/收益比不划算；当合规审计明确要求"即使 KMS 妥协也要 PII 不可暴力"时再考虑。

> **迁移路径（落地约束）**：既有 BCrypt 数据无法直接转 HMAC——需要在用户/儿童首次提供完整 RRN 时把 HMAC 列回填；过渡期双读单写（先读 HMAC 命中，未命中回退候选集 + BCrypt 匹配）。具体步骤留待 Implementation 子 ADR 固化。

## 后果（Consequences）

- **正面**：
  - 即使数据库泄露，RRN 后位不可还原；语义、命名、实现、文档四方一致。
  - 一次性消除 OQ-SEC-4 矛盾与全库"암호문 误述"债务。
  - 若采纳 (b)：消除按 RRN 查找的扩展性退化，并使"防重复 RRN"成为可强制的 DB 约束。
- **负面 / 代价**：
  - RRN **无法展示/找回**（产品须接受"只能校验、不能回显"）。
  - 列改名 + 注释更正 + ERD 重新派生需协同。
  - 若采纳 (b)：需引入 pepper 的密钥管理 + 迁移既有 BCrypt 值（过渡期双读单写）。
- **影响范围**：`children`/`teachers`/`guardians` schema、`AuthService` 注册、`ChildrenService.getChildEntityByRRN`、DBML、ERD `.mmd`、安全/数据/产品/演进类文档。

## 考虑过的备选（Alternatives Considered）

- **可逆加密（复用 `AesGcmCryptoUtil`）** — 否决（维护者明确）：无"回显 RRN"的业务需求，可逆即增加泄露面。
- **明文存储** — 否决：PII 合规不可接受。
- **外部令牌化 / 密钥保险库（Vault/KMS tokenization）** — 暂不采用：相对当前规模过重，可作为未来演进。

## 关联（References）

- 上游：[security-architecture.md §4](../../architecture/security-architecture.md)、[data-architecture.md §6](../../architecture/data-architecture.md)、[open-questions.md](../../modernization/open-questions.md)（OQ-SEC-4）。
- 关联 ADR：[ADR-0009](ADR-0009-restore-auth-enforcement.md)（密钥管理路径复用）。
- 代码：`AuthService.java:182-197,234-255`、`ChildrenService.getChildEntityByRRN`、`AesGcmCryptoUtil.java`、`01_create_schema.sql:683-685/729-731/845-847`。
- **文档勘误清单**（Design 阶段已完成）：`security-architecture.md §4`、`data-architecture.md §6`、`product/glossary.md`、`current-state-assessment.md`、`open-questions.md` OQ-SEC-4。
- **延后到 Implementation 的源文件勘误**（属代码/schema）：
  - `db/initdb/01_create_schema.sql` 列名 `rrn_encrypted` → `rrn_hash` + 注释更正；`db/dbml/schema.dbml` 同步；`docs/db/ERD/diagrams/Kindergarten ERD.mmd`（由 schema 重新派生，**不**手工编辑）。
