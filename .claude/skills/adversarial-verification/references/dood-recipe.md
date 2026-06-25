# DooD（Docker-out-of-Docker）动态验证配方

仅**深度档**用。把"可动态验证"的 finding 交给一次真实跑动来坐实/证伪。本机无 Java/Node，故全程通过容器跑。

## 后端：testcontainers 全套件
关键点（漏一个就会卡在 redis 端口等待 / initdb 路径失败，且真因藏在 `build/test-results/**/*.xml` 而非控制台）：
- **挂 repo 根**进容器，**不是** `backend/` 子目录（initdb 与 seed 按 repo 根布局解析）。
- env 设 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`（容器内连宿主 docker）。
- **关闭 Ryuk**：`TESTCONTAINERS_RYUK_DISABLED=true`。
- 挂 docker socket（`/var/run/docker.sock`）以让容器内再起 testcontainer。
- **验证 seed 改动**：必须 `gradle cleanTest` 后再跑——seed 文件不在 test 任务输入里，否则 Gradle 判 `UP-TO-DATE` 跳过，看似通过实则没跑。
- 失败诊断：读 `build/test-results/test/*.xml` 里的 `<failure>`，不要只看 stdout。

## 前端：lint / build
- 用 `node:20` 容器 DooD 跑 `npm ci` + `npm run lint` / `npm run build`。
- 注意 React 19 / Next 16 的 lint 坑（见项目记忆）。
- **提交前还原 `next-env.d.ts`**（build 会改写它，勿把该改动带进提交）。

## 判定"跑不起来 → 回退静态"
满足任一即回退（verdict 维持 unverified，method=static，note 注明"未动态坐实"）：
- 宿主无 docker / socket 不可挂。
- 镜像拉取或依赖安装在合理时间内失败且非被测问题所致。
- 套件因环境（非被测 finding）原因整体红，无法隔离目标用例。

回退不是失败——如实标注"未动态坐实"比强行下结论更诚实，报告"覆盖与局限"会列出供人工确认。
