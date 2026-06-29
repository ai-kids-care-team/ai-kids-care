# Git Hooks（版本化）

本目录存放随仓库分发的 git hooks。`core.hooksPath` 是**本地 git 配置**，不随 clone 自动生效——
**每个新克隆需执行一次**：

```sh
git config core.hooksPath .githooks
```

## pre-push —— 前端 lint 本地门禁

推送前，若本次推送包含 `frontend/` 改动，自动跑 ESLint，失败则拦截 push。
目的：lint 这类低级错误在本地暴露，不再等到 CI 才发现。

- 选 **pre-push** 而非 pre-commit：commit 仍快，只在真正进入 CI 前把关一次。
- 无 `frontend/` 改动的推送：零额外耗时。
- 优先用 PATH 上的 node（本机已具备）；无 node 时回退 `node:20` docker 容器。
- 紧急绕过（不建议）：`git push --no-verify`。

> 完整 build 不进 hook（较慢），仍由 CI（`.github/workflows/frontend-lint-build.yml`）负责。
