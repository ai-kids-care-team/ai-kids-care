## 1. INC-003：loader PII 投影扫描守卫

- [x] 1.1 `LoaderPiiProjectionGuardTest`（backend 纯单测）：正则扫 `../db/ne4j_kindergartens/*.py` Cypher 属性绑定违例;禁字段集取自 INC-003 requirement
- [x] 1.2 断言无违例 + loader 文件数>0(防空过);loader 当前 CLEAN → 绿(未发现泄露)
- [x] 1.3 容器内绿(t=1)

## 2. INC-005：MapStruct 每-mapper 未映射 = 编译错误

- [x] 2.1 22 个 `@Mapper` 全加 `unmappedTargetPolicy = ReportingPolicy.ERROR`(+ 非通配 import 补 ReportingPolicy)
- [x] 2.2 容器内 compileJava：**0 未映射错误** —— 既有 mapper 本就完整(toVO 全字段名匹配;写路径已对 id/时间戳/关系实体 ignore),无需补 `@Mapping`/`ignore`。22 个 MapperImpl 正常生成
- [x] 2.3 `MapperCompletenessTest`：ClassMapper 往返(非 ignore 字段映射正确含 String→StatusEnum;ignore 字段保持 null)（绿）
- [x] 2.4 编译+测试全过;未改任何既有字段映射语义(仅加注解)

## 3. schema-digest：迁移终态结构守卫 + dbml 回填

- [x] 3.1 `SchemaConsistencyGuardTest extends BaseIntegrationTest`：对 Flyway 全量迁移**终态** schema 结构断言(V7 push_subscriptions↔device_tokens、V3 notifications 可空、V4–V6 rrn_hash NOT NULL+无 rrn_encrypted、V2 audit_logs 可空+correlation_id、代表性唯一索引)（5 测试绿）
- [x] 3.2 〔**apply 期纠正,取消**〕不加「initdb=V1 严格镜像」断言 —— 经核 initdb 非 V1 严格镜像(已含 V4 rrn_hash),迁移幂等容忍、两路径收敛;强行断言会误报。守卫改由 3.1 的终态断言承担。spec/design 已据实更新
- [x] 3.3 dbml 全量回填:经核实际仅 notifications(V3:sent_at/fail_reason→nullable、retry_count default 0)漂移(V2/V4–V7 dbml 本已对齐);已修。dbml 非运行时载荷、无自动 drift 守卫,靠改迁移即同步+评审维持
- [x] 3.4 schema 守卫容器内绿(t=5)

## 4. Spec 核对与验证（verification-before-completion）

- [x] 4.1 `specs/rebuild-guardrails/spec.md` delta：三项移出、backlog 清空(validate 通过)
- [x] 4.2 `specs/data-platform/spec.md` delta：INC-005 + schema 一致性守卫 requirement 与实现一致(apply 期据实修正:去 initdb=V1 镜像/自动 dbml-drift 的过度承诺)
- [x] 4.3 容器内全套件全绿:**148 tests / 2 skipped(@Disabled) / 0 failures**(既有 141 + 新增 7:loader 1/mapper 1/schema 5)
- [x] 4.4 范围核对(git diff):src/main 改动**仅 22 个 mapper 注解**;无其它产品码、无 loader/运行时 schema 改动;dbml 为 doc 对齐
- [x] 4.5 code review(sonnet)完成,Ready to merge;采纳 C1(INC-003 正则加 map-entry 模式+去注释+诚实 Javadoc)/I1(constraint-backed index 注释),重跑全绿
- [ ] 4.6 合并 develop / push / `/opsx:archive`（用户驱动，含 spec delta sync）

---

> 风险/无高风险迁移：本 change **不含**删除/迁移/schema 破坏性操作（无新 Flyway 迁移、不改运行时 schema）；schema 源文修复是 dbml/initdb 与现有迁移的对齐性文档改动。INC-005 的 22-mapper 改动是主要审查点（误用 ignore 掩盖真漏映射）——见 tasks 2.2。
