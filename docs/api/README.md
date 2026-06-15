# 接口文档（API）

## 用途

本目录记录系统对外/对内的**接口契约**：后端 REST API、AI 推理服务 API、图查询 API。它帮助调用方知道"**有哪些接口、怎么调**"。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [rest-endpoints.md](rest-endpoints.md) | 后端 REST 端点目录（按域），认证端点详列 |
| [ai-service-api.md](ai-service-api.md) | AI 推理服务（FastAPI）端点 |
| [graph-api.md](graph-api.md) | Neo4j 关系图查询端点 |

## 权威来源：Swagger / OpenAPI

> ✅ 后端集成 **springdoc-openapi**。**运行时的 Swagger UI 是请求/响应 schema 的权威来源**：
> - Swagger UI：`http://localhost:8080/swagger-ui/index.html`
> - OpenAPI JSON：`http://localhost:8080/v3/api-docs`
>
> 本目录文档提供**结构性总览与导航**；字段级细节请以 Swagger 为准（本文档可能随代码演进而滞后）。

## 通用约定

- ✅ 基址：`/api/v1`（生产经 Nginx `/api/` 反代到 `backend:8080`）。
- ✅ 资源命名：`snake_case` 复数（如 `/cctv_cameras`、`/detection_events`）。
- ✅ 列表接口支持分页（Spring `Pageable`：`page`/`size`/`sort`，默认 `size=20`）。
- ✅ 入参用 DTO，出参用 VO（见 [backend-architecture](../architecture/backend-architecture.md)）。
- ✅ **鉴权已启用**（PR #89）：服务端会话 + 默认拒绝 + 每请求授权；前端去 JWT、改 cookie + CSRF（见 [security-architecture](../architecture/security-architecture.md)）。
- ⚠️ 无统一错误响应格式（OQ-ARCH-2）。
