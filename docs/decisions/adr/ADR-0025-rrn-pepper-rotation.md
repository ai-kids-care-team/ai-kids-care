---
ADR: ADR-0025
title: "ADR-0025: RRN pepper 的密钥版本化与轮换机制"
status: Proposed
implementation: Not Started
date: 2026-06-17
deciders: 接手人（Lead）起草，维护者待 Accept（密钥轮换 / PII，归维护者拍板）
supersedes: []
superseded_by: null
related_specs: [SPEC-0001]
---

# ADR-0025: RRN pepper 的密钥版本化与轮换机制

> 维护者已决「RRN 的 pepper 必须可轮换」（见 [ADR-0024](ADR-0024-rrn-bcrypt-to-hmac-migration.md) 维护者已决 §2）。本 ADR 把**轮换机制**从迁移 ADR 中拆出独立决断，**建立在 ADR-0024 之上**（RRN 已经在 HMAC-SHA-256 + 单一 pepper 下）。
> 一句话：引入 **pepper 密钥版本化** + **持有明文时机的懒重算**（复用 ADR-0024 的 RRN 再校验流程），使旧 pepper 可在**硬门**下安全退役。

## 状态（Status）

Decision: `Proposed`（接手人起草，维护者待 Accept）

Implementation: `Not Started`

> **依赖 ADR-0024 已落地**（RRN 在 HMAC + 单一 pepper）。本 ADR 可独立排期，不阻塞 ADR-0024 的迁移上线。高风险（密钥 + PII）。

## 背景（Context）

- ADR-0024 落地后，RRN 以 `HMAC-SHA-256(pepper, first6‖back7)` 存于 `rrn_hash`，**单一 pepper**，无版本概念。
- **轮换的根本难点**：HMAC 是带密钥的 PRF——更换 pepper 后，旧 hash 无法用新 pepper 重现，而**明文不可从旧 hash 反推**。因此「换一个 pepper 并重算全部行」无法在后台一次性完成；只能在**持有明文的时机**重算。
- **威胁动机**：pepper 疑似泄露时必须能更换并使旧 pepper 失效，否则 `rrn_hash` 退化为可离线穷举（10^7 空间，ADR-0010 §87）。无轮换能力 = 一次泄露永久暴露。
- **可复用的明文时机**（来自 ADR-0024）：① 儿童经 `getChildEntityByRRN` 读路径；② 保护者/教师经 **RRN 再校验流程**（D7）。

## 决策（Decision）

### R1. 密钥版本列

- 三表（`children`/`guardians`/`teachers`）`ADD COLUMN rrn_hash_key_version varchar`。
- 上线本 ADR 时，把 ADR-0024 写入的存量行**标注**为初始版本（如 `v1`）——纯 `UPDATE`，不需明文（只是给既有单 pepper hash 贴版本标签）。随后 `SET NOT NULL`。

### R2. 版本化 pepper 配置

- `application.yml`：
  - `rrn.hash.peppers`：**版本→pepper 映射**，如 `{ v1: ${RRN_HASH_PEPPER_V1}, v2: ${RRN_HASH_PEPPER_V2} }`（裸引用、fail-fast）。
  - `rrn.hash.current-version`：新写使用的版本。
  - **active 版本集** = `current` ∪ {`rrn_hash_key_version` 仍有存量行的旧版本}。
- 下游调用方（compose / `.env.example` / `application-test.yml` / `compose-config.yml` dummy env）**每个 active 版本一个 env 槽**。

### R3. 读 / 写改造（在 ADR-0024 基础上）

- **写**：`RrnHashUtil.hash(peppers[current], …)`，存 `rrn_hash` + `rrn_hash_key_version = current`。
- **读**（`getChildEntityByRRN`）：对**所有 active 版本**各算一个 hash → `repository.findByRrnHashIn(hashSet)` 一次命中（稳态 active=1，轮换重叠期=2，仍是索引等值，O(1) 量级）。命中后若该行 `key_version != current` → **同事务重算并写回 current**（懒迁；此刻持有明文）。
- `ChildRepository`：`findByRrnHash` → `findByRrnHashIn(Collection<String>)`。

### R4. 保护者 / 教师行的重算

- 与 ADR-0024 的硬门同理：保护者/教师本人 RRN **无 read-by-rrn 触发点**，旧版本行只能经 **ADR-0024 D7 的 RRN 再校验流程**重算到 current（复用，不新建机制）。

### R5. 轮换流程与退役硬门

1. 在 `peppers` 加新版本，置为 `current`。
2. 旧版本行经 R3（儿童读路径）/ R4（保护者教师再校验）懒迁到 current。
3. **退役硬门**：`SELECT count(*) FROM children/guardians/teachers WHERE rrn_hash_key_version <> :targetVersion` **= 0** 时，方可从 `peppers` 撤下旧 pepper 并销毁。**不接受残留**（与 ADR-0024 删列门控同philosophy）。
- 退役前旧 pepper 必须保留（否则旧版本行无法校验/迁移）。

## 验证（Compliance）

```
bash scripts/test-backend.sh '*RrnHash*' '*Children*' '*Auth*'
bash scripts/test-backend.sh '*Sensitive*' '*PublishedOpenApi*'   # rrn_hash_key_version 不得泄漏
bash scripts/schema-digest.sh                                     # R1 迁移后重生成
```
- 新增测试：多版本查找命中（current + 旧版本各一行）；命中旧版本后懒迁到 current；退役硬门核对查询（残留 → 拒绝退役）；test profile 注入多版本 pepper。

## 后果（Consequences）

- **正面**：pepper 可在硬门下安全退役 → 泄露可补救；版本化对调用方透明（active 集驱动查找）。
- **负面 / 代价**：多一列 + 多版本查找（稳态 1，重叠期 2）；轮换退役依赖全量懒迁完成，可能拖很久（受最慢的「未再校验用户」制约）；过渡期须同时持有新旧 pepper。
- **影响范围**：三表 schema（加列）、`RrnHashUtil`、`ChildrenService`/`ChildRepository`、版本化配置 + 下游调用方、复用 ADR-0024 的再校验流程。

## 风险与回滚

- 旧 pepper 在其行数归 0 前**不得**销毁（否则旧版本行不可校验）。
- 退役不可逆（pepper 销毁后无法回滚到旧版本）→ 受退役硬门 + 维护者签署保护。
- 与 pepper 丢失同源风险：任一 active 版本 pepper 丢失 = 该版本行校验失效；所有 active 版本均须纳入密钥管理与备份。

## 考虑过的备选（Alternatives Considered）

- **单一永久 pepper、不轮换**（ADR-0024 现状）：维护者否决——一次泄露永久暴露。
- **轮换时后台批量重算**：不可行——明文不可从旧 hash 反推；只能持有明文时重算。
- **保留所有历史 pepper、从不退役**：否决——等于不轮换的安全收益（旧 pepper 泄露仍暴露旧行）。
- **加密 pepper（用 KMS 包裹）替代轮换**：正交手段，可叠加；但不替代「换 key 使旧哈希失效」的能力，且引入 KMS 依赖，超出当前规模——记为未来演进。

## 关联（References）

- 依赖：[ADR-0024](ADR-0024-rrn-bcrypt-to-hmac-migration.md)（RRN→HMAC 单 pepper；D7 再校验流程被本 ADR 复用）。
- 上游：[ADR-0010](ADR-0010-rrn-one-way-hash.md)（pepper 安全模型 §85-89）、[ADR-0009](ADR-0009-restore-auth-enforcement.md)（密钥路径）。
- 代码（落地时）：`RrnHashUtil`、`ChildrenService.getChildEntityByRRN`、`ChildRepository.findByRrnHashIn`、`AuthService` 写路径、版本化配置。
