## 1. 前置门（维护者审批）

- [x] 1.1 维护者审阅并批准（破坏性，apply 前必须）

## 2. 清空后端测试

- [x] 2.1 删除 `backend/src/test/**` 全部测试类（含 `harness/` 守卫与产品测试）
- [x] 2.2 删除 `backend/src/test/resources` 测试资源（整个 `backend/src/test` 已移除）

## 3. 回退构建强制

- [x] 3.1 `build.gradle` 移除 `-Amapstruct.unmappedTargetPolicy=ERROR` 块（INC-005），回退默认

## 4. 删除 schema-digest 三件套

- [x] 4.1 删除 `docs/engineering/schema-digest.md`（`docs/engineering/` 已空，目录移除）
- [x] 4.2 删除 `scripts/schema-digest.sh`
- [x] 4.3 删除 `.github/workflows/schema-digest-drift.yml`

## 5. 删除无-JDK 测试回路

- [x] 5.1 删除 `scripts/test-backend.sh`（`scripts/` 已空，目录移除）
- [x] 5.2 `settings.json` 移除 `permissions.allow` 中 `Bash(bash scripts/test-backend.sh*)` 项

## 6. 停用 Backend Java Tests CI

- [x] 6.1 删除 `.github/workflows/backend-java-tests.yml`
- [ ] 6.2 维护者手动：在 GitHub `main` 分支保护的 required status checks 中移除
      **`Gradle test (Java 21)`** 与 **`schema-digest matches migrations`** 两项（job 名，见 ADR-0020；
      服务端设置，非仓库文件）。否则 develop→main release PR 会卡在「等待状态」。

## 7. 验证与提交

- [x] 7.1 grep 确认无残留引用：保留的 CI（release/compose/frontend/ai）与 `build.gradle` 干净；
      docs/* 中的引用属 Change 3 范围，预期内
- [x] 7.2 保留的 CI 与产品代码（`backend/src/main`、前端、ai、db）未受影响
- [x] 7.3 `git diff --check` 干净；提交 develop
