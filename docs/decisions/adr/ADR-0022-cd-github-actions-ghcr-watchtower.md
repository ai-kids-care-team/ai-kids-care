---
ADR: ADR-0022
title: "ADR-0022: CD 改用 GitHub Actions（release-tag 构建 + GHCR 私有镜像 + watchtower 自动部署），退役 Jenkins"
status: Accepted
implementation: Not Started
date: 2026-06-16
deciders: 接手人起草，维护者 Accept（2026-06-16）
supersedes: []
superseded_by: null
related_specs: []
---

# ADR-0022: CD 改用 GitHub Actions + GHCR + watchtower，退役 Jenkins

> 本 ADR 决定持续部署（CD）的架构：从 **Jenkins 自托管 pull 式部署** 迁移到 **GitHub Actions 在 release tag 触发构建并推送 GHCR 私有镜像、目标机上 watchtower 自动拉取部署**，并退役 Jenkins。与 [ADR-0020](ADR-0020-branch-protection-release-model.md)（main=人工发布门）、[ADR-0012](ADR-0012-production-data-lifecycle.md)（生产数据生命周期）、[ADR-0017](ADR-0017-tls-https-termination.md)（TLS）相关。落地（新增 workflow、改 compose、退 Jenkins、配 GHCR/host）委派 Implementation。

## 状态（Status）

Decision: `Accepted`（2026-06-16 维护者 Accept）

Implementation: `Not Started`

> 维护者于 2026-06-16 拍板：OQ-1 演示数据 = **持久**（initdb 首次灌种子一次 + 持久卷 + Flyway 增量；watchtower 重建容器不清卷；与未来 prod 路一致）。落地（`release.yml` + compose 改造 + 退 Jenkins + GHCR/host 配置）委派后续 Implementation。

## 背景（Context）

### As-built 事实（经 `Jenkinsfile`/`docker-compose*.yml`/`docs/operations/deployment.md` 核实）

- 整栈用 **Docker Compose**：根 compose 编排 `db`(Postgres)/`neo4j`/`data-loader`/`backend`/`frontend`；AI 服务在独立 `ai/docker-compose.yml`。镜像由各 `Dockerfile` 多阶段构建。
- **当前 CD = Jenkins（自托管，`jenkins/` 目录容器化）挂 `develop`**：`Jenkinsfile` 阶段 = Checkout → `gradlew test` → **Demo Deploy (CI Reset)**（`docker compose down --remove-orphans --volumes --rmi local` + `up -d --build`，每次**清空数据卷**重建演示）。
- **生产部署是手动的**：`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`（`Jenkinsfile` 注释明确"不放进 CI"）。**镜像在 host 本地构建，无 registry/GHCR**。
- **测试已在 GitHub Actions 运行**（`Backend Java Tests` 等三 workflow）→ Jenkins 的测试阶段**已冗余**。
- **部署目标 = 单台 Windows 11 机器，当前仅作演示用途，无独立生产环境**。维护者目标为"铺好 CD 管线之路"，真上生产为后续；故该机现为 demo、未来即 prod，**同一条管线**。
- `docs/operations/deployment.md` 标注生产 compose 路径**尚未生产就绪**（data-loader 与 V1 迁移竞态、默认凭据、CSV 快照、备份策略 OQ-OPS-4 未定、Caddy TLS 端到端待验证）。

### 约束与诉求

- 部署目标是 Windows 机：**self-hosted GitHub Actions runner 需解决后台运行/开机自启**，对单人维护是额外成本，**否决在目标机常驻 runner 的方案**。
- 维护者希望一条**易记的团队规定**："**在 main 打 tag = 发布**"，避免久而遗忘发布步骤。
- 安全敏感（儿童数据方向）：构建制品不宜公开。

## 决策（Decision）

采用 **GitHub Actions 构建 + GHCR 私有镜像 + 目标机 watchtower 自动部署**，触发于 main 的 release tag；退役 Jenkins。

1. **触发 = main 上 `v*` git tag**（`release.yml` 用 `on: push: tags: ['v*']`）。`develop` 只跑 CI、**不部署**。
2. **构建在云端**：`release.yml` 在 GitHub 托管 runner 构建 `backend`/`frontend`/`db` 镜像（**AI 服务不纳入初版**，见 §6）。**目标机不构建**——这绕过 Windows self-hosted runner 的后台/自启成本（方式 C，跳过 A）。
3. **冒烟门**：推"部署触发 tag"前，先在 runner 里把整栈 `up` 起来过 healthcheck；**只有冒烟绿才推 `:prod`**——坏镜像不触发部署。
4. **双 tag + GHCR 私有**：每次 release 推 `:<版本>`（不可变、用于回滚）+ `:prod`（可变、watchtower 盯）到 **GHCR 私有**包。回滚 = 把 `:prod` 重指旧 `:<版本>`。私有 → 目标机/watchtower 需 `read:packages` token，制品不外泄。
5. **目标机自动部署 = watchtower**：演示机（Windows Docker Desktop）上 compose 含一个 `watchtower` 容器（`restart: unless-stopped`，随 Docker Desktop 起；非独立 Windows 服务），盯 `:prod` digest 变化 → 自动 `pull` + 重建容器。**数据策略（待确认 OQ-1）：推荐持久**——`db` 用含 initdb 的镜像首次灌种子一次，之后**持久卷**，schema 由 Flyway 增量（与未来 prod 路一致；watchtower 不清卷）。若要"每次清卷重灌 always-fresh 演示"则改用 Windows Task Scheduler 脚本（`pull && down --volumes && up`）。
6. **compose 改造**：部署用 compose（prod override）把相关服务 `build:` 换为 `image: ghcr.io/ai-kids-care-team/<svc>:prod`，并加 `watchtower` 服务 + 持久卷。
7. **退役 Jenkins**：测试已由 Actions 覆盖；演示由本管线承担；移除根 `Jenkinsfile` 与 `jenkins/`。
8. **可选人工门（GitHub Environments）**：可在"推 `:prod` tag"步标 `environment: production` + required reviewers——该 job 在 Actions 运行页**暂停等维护者点 Approve** 才继续（部署历史可审计；亦可作用域化生产密钥）。**当前 greenfield 未上线，先不加**（冒烟门已挡坏镜像），临近真上线再加。
9. **单机即 demo/future-prod**：同一条管线。真上生产时切到 `db/Dockerfile.prod`（无 initdb 种子）+ 生产 `.env`（凭据/`SESSION_COOKIE_SECURE`/Caddy `DOMAIN`/`ACME_EMAIL`）+ 备份，先解决 §背景 的生产就绪项。

## 方案比较（Options）

| 方案 | 怎么够到目标机 | 结论 |
| --- | --- | --- |
| A. 目标机常驻 self-hosted runner | runner 在机上跑部署 | **否决**：Windows 后台运行/开机自启成本高 |
| B. 云 runner 经 SSH 部署 | Actions SSH 进机 | 未采用：SSH 私钥进 Secret、机需可达；不如 C 的不可变制品 |
| C. 构建推 GHCR + 机上 watchtower 拉取（**采纳**） | 机不构建、只拉镜像 | 绕过 runner 后台成本；不可变制品、可版本化回滚；"tag=发布"易记 |

部署触发：**watchtower 自动（C2）** + 冒烟门 + 双 tag（采纳，满足"只记 tag=发布"）；手动一条命令（C1）作为退路。

## 后果（Consequences）

- **正面**：一条易记规则（main 打 tag = 发布）；构建移云端、Windows 机不构建、无 self-hosted runner；不可变镜像 + 双 tag 回滚；统一到 GitHub Actions、退 Jenkins；管线即"prod 路子"，未来切真 prod 复用同管线。
- **负面 / 代价**：watchtower **无人值守部署**——靠冒烟门挡坏镜像，真上线前建议加 Environments 审批门；GHCR 私有 → 目标机需管一个 pull token；演示从"每次清卷重灌"变为**持久**（OQ-1，若坚持清卷需 Task Scheduler 脚本）；生产就绪项（loader 竞态/凭据/备份/TLS）仍需在真打 release tag 上线前完成（ADR-0012/0017）。
- **影响范围**：新增 `.github/workflows/release.yml`（+ 复用冒烟 job）；`docker-compose.prod.yml`（GHCR `image:` + watchtower + 持久卷）；移除 `Jenkinsfile`/`jenkins/`；GHCR 包与 host 的 `read:packages` token；`docs/operations/deployment.md` 同步。

## 合规与验证（Compliance）

- `release.yml` 仅在 `v*` tag 触发；冒烟门绿才推 `:prod`；镜像推 **GHCR 私有**；AI 不纳入初版。
- 部署 compose 引用 GHCR `:prod` 镜像（非 `build:`）；watchtower 盯 `:prod`。
- Jenkins（`Jenkinsfile`/`jenkins/`）移除后，CI 测试仍由 Actions 覆盖。
- 真上生产前完成 ADR-0012（loader/备份）/ADR-0017（TLS）的生产就绪项；`deployment.md` 与本 ADR 一致。

## 开放问题（Open Questions）

- ~~**OQ-1**~~（**已定 2026-06-16：持久**）：initdb 首次灌种子一次 + 持久卷 + Flyway 增量；需全新种子时手动重置一次。
- **OQ-2**：GHCR pull token 的发放与轮换方式（host 上如何安全存放）。
- **OQ-3**：何时加 Environments 人工审批门（建议临近真上线）。
- **OQ-4**：常驻演示站的对外暴露/访问方式（端口/Caddy/域名）——与 ADR-0017 TLS 协同。

## 关联（References）

- [ADR-0020](ADR-0020-branch-protection-release-model.md)（main=人工发布门；本 CD 挂 main release tag）、[ADR-0012](ADR-0012-production-data-lifecycle.md)（演示 vs 生产数据生命周期）、[ADR-0017](ADR-0017-tls-https-termination.md)（Caddy TLS）。
- `Jenkinsfile`、`jenkins/`、`docker-compose.yml`、`docker-compose.prod.yml`、`docs/operations/deployment.md`。
