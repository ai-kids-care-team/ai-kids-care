# 运维文档（Operations）

## 用途

本目录面向**部署与运行系统**：如何构建发布、如何配置、出问题怎么排查、有哪些可观测手段。它回答"**怎么把系统跑在某个环境上并保持健康**"。

面向读者：负责部署/运维/值班的人，以及需要理解运行时配置的开发者。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [deployment.md](deployment.md) | Docker Compose 整栈、CD 管线（GitHub Actions + GHCR + watchtower，ADR-0022）、镜像构建 |
| [configuration.md](configuration.md) | 环境变量矩阵与默认值、端口、时区 |
| [runbook.md](runbook.md) | 常见运维任务与故障排查 |
| [observability.md](observability.md) | 日志、健康检查、监控现状 |

## 现状提示（必读）

> ⚠️ 本项目处于 **demo/单人维护** 阶段，运维成熟度有限。多处配置（默认密码、DEBUG 日志、CI 删卷）仍更像开发态而非生产态（**鉴权已于 PR #89 启用**：默认拒绝 + 服务端会话）。涉及生产部署前，请先核对 [modernization/open-questions.md](../modernization/open-questions.md) 中的待确认项与生产 `.env`（`SESSION_COOKIE_SECURE=true`、Caddy `DOMAIN`/`ACME_EMAIL`）。
