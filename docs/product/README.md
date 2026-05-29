# 产品文档（Product）

## 用途

本目录从**产品与业务视角**描述 AI Kids Care：它解决什么问题、服务哪些角色、提供哪些能力、关键领域概念如何定义。它回答"**这个系统是做什么的、为谁而做**"，而不涉及实现细节（实现见 [architecture/](../architecture/README.md)）。

面向读者：新加入的工程师、产品/运营、需要快速建立业务心智模型的任何人。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [overview.md](overview.md) | 产品愿景、目标用户、核心价值、业务流程总览 |
| [personas-and-roles.md](personas-and-roles.md) | 五类用户角色、权限范围（scope）与典型场景 |
| [features.md](features.md) | 功能能力清单，映射到后端 API 与数据表 |
| [glossary.md](glossary.md) | 领域术语表（中／韩／英对照） |

## 信息来源与可信度

本目录内容主要从以下代码证据逆向梳理：根 `README.md`（韩/英/中三语）、数据库 schema（`db/initdb/01_create_schema.sql`）、后端控制器（`backend/.../controller/`）、前端路由（`frontend/src/app/`）。

> ⚠️ 产品意图（如商业模式、目标市场、优先级）大多**无法仅凭代码证明**，相关推断均已标注 🔶 或归入 ❓ 待确认。本项目无 `docs/product` 之外的产品需求文档（PRD），是 [open-questions](../modernization/open-questions.md) 的重点缺口之一。
