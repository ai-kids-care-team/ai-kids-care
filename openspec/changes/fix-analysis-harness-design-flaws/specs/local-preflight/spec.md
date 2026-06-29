## ADDED Requirements

### Requirement: 前端 lint MUST 在本地 pre-push 阶段拦截

仓库 MUST 提供一个在 `git push` 前运行的本地预检，对包含 `frontend/` 改动的推送执行 ESLint，校验失败时 MUST 阻止推送。目的是让 lint 这类低级错误在本地暴露，而非等到 CI 才发现。预检 MUST 选用 pre-push 而非 pre-commit，以保证 commit 仍然快速、仅在真正进入 CI 前把关。

#### Scenario: 前端有改动且 lint 失败时阻止推送
- **WHEN** 本次推送包含 `frontend/` 下的改动且 ESLint 报错
- **THEN** pre-push 预检以非零退出码终止，`git push` 被拒绝
- **AND** 输出指明失败的 lint 规则与文件，便于本地修复

#### Scenario: 无前端改动时不拖慢推送
- **WHEN** 本次推送不含 `frontend/` 改动
- **THEN** 预检跳过前端 lint，不引入额外耗时

### Requirement: 预检 MUST 在本机工具约束下可落地并随仓库分发

预检 MUST 适配本机约束：Claude settings hook 解释器仅支持 git/powershell，因此本地预检以版本化的 `.githooks/pre-push`（POSIX sh，经 Git Bash 执行）实现，并通过 `git config core.hooksPath .githooks` 随克隆分发，不依赖手动安装到 `.git/hooks`。预检 MUST 对 node 缺失健壮：优先用 PATH 上的 node，缺失时回退到 `node:20` docker 容器。

#### Scenario: 通过 core.hooksPath 分发
- **WHEN** 新克隆仓库并配置 `core.hooksPath .githooks`
- **THEN** pre-push 预检无需手动复制即生效
- **AND** hook 脚本以 POSIX sh 编写，可经 Git Bash 在本机运行

#### Scenario: node 缺失时回退 docker
- **WHEN** 执行环境的 PATH 上没有 node
- **THEN** 预检回退到 `docker run --rm -v "$PWD/frontend":/app -w /app node:20` 执行 ESLint
- **AND** 两条路径产出一致的拦截行为

### Requirement: 预检范围 SHALL 限于快速检查

pre-push 预检 SHALL 仅包含快速检查（lint），MUST NOT 包含耗时的构建（`npm run build`），后者留给 CI，以免拖慢日常推送。

#### Scenario: 构建不进 pre-push
- **WHEN** 审阅 `.githooks/pre-push`
- **THEN** 其中只运行 lint，不运行 `npm run build`
- **AND** 完整 build 仍由 `frontend-lint-build.yml` 在 CI 执行
