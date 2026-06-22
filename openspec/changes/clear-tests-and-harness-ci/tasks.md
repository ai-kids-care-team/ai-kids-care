## 1. 前置门（维护者审批）

- [ ] 1.1 维护者审阅并批准（破坏性，apply 前必须）

## 2. 清空后端测试

- [ ] 2.1 删除 `backend/src/test/**` 全部测试类（含 `harness/` 守卫与产品测试）
- [ ] 2.2 删除 `backend/src/test/resources` 测试资源（如有）

## 3. 回退构建强制

- [ ] 3.1 `build.gradle` 移除 `-Amapstruct.unmappedTargetPolicy=ERROR` 块（INC-005），回退默认

## 4. 删除 schema-digest 三件套

- [ ] 4.1 删除 `docs/engineering/schema-digest.md`
- [ ] 4.2 删除 `scripts/schema-digest.sh`
- [ ] 4.3 删除 `.github/workflows/schema-digest-drift.yml`

## 5. 删除无-JDK 测试回路

- [ ] 5.1 删除 `scripts/test-backend.sh`
- [ ] 5.2 `settings.json` 移除 `permissions.allow` 中 `Bash(bash scripts/test-backend.sh*)` 项

## 6. 停用 Backend Java Tests CI

- [ ] 6.1 删除 `.github/workflows/backend-java-tests.yml`
- [ ] 6.2 维护者手动：GitHub 分支保护 required checks 去掉 "Backend Java Tests"（服务端设置，非仓库文件）

## 7. 验证与提交

- [ ] 7.1 grep 确认无残留引用（`schema-digest`、`test-backend.sh`、`incidents.md` MapStruct、backend `harness` 测试）
- [ ] 7.2 确认保留的 CI（compose/frontend/ai/release）与产品代码未受影响
- [ ] 7.3 `git diff --check` 干净；提交 develop
