# Finding Schema 与示例（统一数据契约）

所有四位角度分析师产出的每一条 finding 都遵循此 schema，便于 lead 去重、定级、互链。

## 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | `<ANGLE>-NN`，前缀：`ARC-`(架构) / `SEC-`(安全) / `QLT-`(质量) / `INT-`(集成)，NN 两位序号 |
| `angle` | 是 | architecture / security / quality / integration |
| `component` | 是 | backend / frontend / ai / db / infra / cross（跨组件） |
| `severity` | 是 | critical（必须立即修，安全/数据正确性）/ high / medium / low / info |
| `title` | 是 | 一句话概括 |
| `location` | 是 | `path:line`。**integration 必须给边界两侧**，如 `backend/.../DetectionController.java:42 ↔ frontend/.../detectionEvents.api.ts:18` |
| `evidence` | 是 | 最小可佐证的代码片段或命令输出，不要整文件 |
| `description` | 是 | 「是什么」+「为何重要/影响面」 |
| `recommendation` | 是 | 可操作的修复方向（不只吐槽） |
| `confidence` | 是 | high（已验证/精确定位）/ medium（静态推断）/ low（存疑、需人确认） |
| `cross_refs` | 否 | 关联的其他 finding id 列表，用于多角度互链 |

## 严重度判定基准（统一口径，避免各角度尺度不一）

- **critical**：可被利用导致越权/数据泄露/跨租户访问，或会造成生产数据错误/丢失。
- **high**：契约错位致功能静默失效；缺失关键域测试；明确的扩展瓶颈。
- **medium**：可维护性坏味、局部重复、非关键路径缺测试、降级 confidence 的安全推断。
- **low**：风格、命名、轻微不一致。
- **info**：观察项/改进建议，无直接风险。

## 示例

```yaml
- id: SEC-03
  angle: security
  component: backend
  severity: critical
  title: DetectionEvent 查询未在 JPQL 层按 kindergarten_id 过滤
  location: backend/src/main/java/com/ai_kids_care/v1/repository/DetectionEventRepository.java:55
  evidence: |
    @Query("SELECT d FROM DetectionEvent d WHERE d.id = :id")
    Optional<DetectionEvent> findById(@Param("id") Long id);   // 缺 AND d.kindergartenId = :tenant
  description: 该查询按全局 id 取事件，未带租户条件；KG_ADMIN 传他园事件 id 可读到跨租户数据，违反多租户隔离（应 404）。
  recommendation: 在 JPQL 加 `AND d.kindergartenId = :tenant`，并让 service 从会话 activeKindergartenId 注入；补一个跨租户返回 404 的集成测试。
  confidence: high
  cross_refs: [INT-05]
```

```yaml
- id: INT-05
  angle: integration
  component: cross
  severity: high
  title: AI ingest payload 的 dedup_key 字段名与后端 DTO 不一致
  location: ai/src/ai_app/utils/backend_ingest.py:30 ↔ backend/.../dto/DetectionIngestRequest.java:14
  evidence: |
    # ai 端: {"dedupKey": ...}
    // 后端 DTO 字段: private String dedup_key;
  description: 前者驼峰后者下划线，Jackson 默认不映射 → dedup_key 入库为 null，去重失效，重复事件入库。
  recommendation: 统一命名（建议后端 @JsonProperty("dedupKey") 或 ai 端改 key），加一条 ingest 契约测试。
  confidence: medium
  cross_refs: [SEC-03]
```
