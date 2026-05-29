# 关系图查询 API（Graph）

✅ 来源：`GraphController`、`GraphService`、`GraphRepository`（Neo4j 原生 Cypher）、`vo/graph/ChildGraphVO`。架构见 [data-architecture](../architecture/data-architecture.md#7-neo4j-加载器)。

## 端点

### `GET /api/v1/graph/children/{childId}`

返回以某儿童为中心的关系图。

✅ 后端在 Neo4j 执行的 Cypher（`GraphRepository.findChildGraph`）：

```cypher
MATCH (ch:Child {child_id: $childId})
OPTIONAL MATCH (c:Class)-[:HAS_CHILD]->(ch)
OPTIONAL MATCH (t:Teacher)-[:HAS_CLASS]->(c)
OPTIONAL MATCH (k:Kindergarten)-[:HAS_TEACHER]->(t)
OPTIONAL MATCH (ch)-[rg:HAS_GUARDIAN]->(g:Guardian)
RETURN ch, c, t, k, collect({guardian:g, relationship:rg.relationship,
                             is_primary:rg.is_primary, priority:rg.priority}) AS guardians
```

✅ 响应（`ChildGraphVO`）结构：

```text
{
  child:        { childId, name, childNo, gender, status },
  classInfo:    { classId, name, grade, academicYear, status },   // 可空
  teacher:      { teacherId, name, staffNo, level, status },       // 可空
  kindergarten: { kindergartenId, name, address, status },         // 可空
  guardians: [  { guardianId, name, gender, status,
                  relationship, isPrimary, priority } ]            // 按 priority 升序
}
```

✅ 行为细节：
- 用 `OPTIONAL MATCH` → 班级/教师/幼儿园可能为 `null`（儿童未分班等情况）。
- `guardians` 按 `priority` 升序排序（`null` 排末尾）。
- 儿童不存在 → 抛 `NoSuchElementException`（"Child graph not found"）。

## 图数据模型

✅ 节点与关系（来自上面的 Cypher）：

```text
(Kindergarten) -[:HAS_TEACHER]-> (Teacher) -[:HAS_CLASS]-> (Class) -[:HAS_CHILD]-> (Child)
(Child) -[:HAS_GUARDIAN {relationship, is_primary, priority}]-> (Guardian)
```

## 前提与边界

- ✅ Neo4j 是 PostgreSQL 的**派生只读视图**，由 data-loader 一次性加载（见 [database-guide](../engineering/database-guide.md)）。
- ❓ PG 数据变更后图不自动同步（OQ-DATA-1）。
- 🔶 前端用 `reagraph` 渲染该响应（见 [frontend-architecture](../architecture/frontend-architecture.md)）。
