# API 契约产物模板

> design 阶段冻结此产物为**前后端唯一真源**,落点 `openspec/changes/<change-id>/api-contract.md`,随 change 走、archive 时一并归档。`backend-implementer` 与 `frontend-implementer` 各自对着这份冻结契约**全并行**实现,互不阻塞;fan-in 时 `integration-analyst` 逐字段比对双侧是否都贴合契约。

## 使用说明

- 每个受影响端点复制一组下面的小节填写。
- **路径命名不统一**:`detection-events`(连字符)vs `detection_sessions` / `cctv_cameras`(下划线)——**以实际 controller 为准**,不要臆造。
- 字段级必须**双侧可读**:后端 DTO/VO 与前端 `services/apis/*.api.ts` 的类型要能逐字段对上。单侧推断不算数。
- **契约含糊 → 回到 design 补清**,不退化成「后端先行」。这是本流水线并行度的前提。

---

## 契约模板(每端点一组)

```markdown
### <端点名> — <一句话职责>

- **路径**:`/api/v1/<path>`（以实际 controller 为准，连字符/下划线不统一）
- **方法**:GET | POST | PUT | PATCH | DELETE
- **鉴权**：
  - 业务 API → 会话（Spring Session + Redis）+ CSRF 头 `X-XSRF-TOKEN`
  - 或 internal → `/api/v1/internal/**`，Bearer `AI_SERVICE_TOKEN`（`ROLE_AI_SERVICE`），CSRF 豁免
- **授权**：service 方法上的 `@PreAuthorize("@authorizationPolicy.isAllowed(...)")`；跨租户/不可见资源 → **404**（隐藏存在性），不返回 403/200

#### 请求
- **DTO 类名**：`XxxCreateDTO` / `XxxUpdateDTO`（Update 用 PATCH 语义，null 字段忽略）
| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| <name> | <type> | 否/是 | <@NotBlank / 范围 / 格式> |
> 注意：前端**绝不传 kindergartenId**（租户由后端会话上下文 `activeKindergartenId` 决定）。

#### 响应
- **VO 类名**：`XxxVO`
| 字段 | 类型 | 可空 | 嵌套 shape/说明 |
|------|------|------|-----------------|
| <name> | <type> | 否/是 | <嵌套 VO 名 / 数组元素类型> |

#### enum
- 涉及 enum：`<enum_name>` = [值1, 值2, …]
- **三处同步**：DB（`*_enum`）/ 后端 `type.*` / 前端 i18n；值单一真源 `GET /api/v1/enums/{name}`，label 归前端 i18n。

#### 分页（若适用）
- 是否分页：是/否
- 后端 Spring `Page<XxxVO>` ↔ 前端 `PageResponse<Xxx>`：`content` / `totalElements` / `totalPages` / `number` / `size` shape 对齐。

#### 错误契约
- 跨租户/不存在 → **404**
- 校验失败 → 400 + `{ field, message }` shape（以既有全局异常处理为准）

#### 前端对齐点
- 对应文件：`frontend/src/services/apis/<name>.api.ts`
- 客户端：RTK Query（`baseApi`）或 Axios（`apiClient`）——两者拦截器均回填 CSRF 头
```

---

## 填写守则

1. **双侧同读**:每个字段的名字/可空性/enum/嵌套 shape,后端与前端读出来必须一致;「后端返回 X 前端读 Y」的错位是 fan-in 门禁的重点猎物。
2. **含糊即回 design**:任何字段类型/可空性/enum 值拿不准 → 回 design 补清,禁止实现者各自臆断。
3. **鉴权显式**:每端点标清会话+CSRF 还是 internal Bearer;写端点默认受 CSRF 保护,唯一豁免是 `/api/v1/internal/**`。
4. **租户不出现在契约参数里**:租户隔离靠后端会话上下文,契约中**不含** kindergartenId 入参。
