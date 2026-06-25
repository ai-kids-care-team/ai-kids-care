---
name: analyze-quality
description: 从代码质量/可维护性/测试角度审查组件——复杂度、重复、死代码、命名、技术债、测试覆盖与测试质量。quality-analyst 使用。当需要可维护性评估、技术债盘点、测试覆盖/测试质量审查、代码异味识别时使用。
---

# analyze-quality — 质量与可维护性角度审查方法

从**接手者视角**审查：「好不好懂、改起来会不会踩雷、回归网够不够密」。按 finding schema 输出（id 前缀 `QLT-`），能量化就给数字。

## 为何这样审（原则）
质量问题是**复利**——今天一处复制粘贴，明天五处不一致。但也别教条：先看周边代码的既有风格（注释密度、命名惯例），判断「偏离」而非照搬外部标准。强加偏好会制造噪音。

## 检查清单（按本工程栈）

### 复杂度与坏味
- 超长函数/类、深嵌套、过多分支；难推理的可变状态。
- 上帝类（与 architecture 角度可能重叠 → cross_ref 互链，不重复定级）。

### 重复与死代码
- 复制粘贴逻辑（多个 service/组件重复同段）。
- **已废弃路径**：AI 中标注「demo stub / to be removed」的 `utils/pushover.py`、`utils/sms.py` 是否仍被引用；retire-dictionary-tables 后是否有残留 CRUD/引用。
- 未用导出、未用依赖（前端 package.json、ai pyproject）。

### 命名与可读性
- 误导性命名、隐晦缩写、非显然逻辑缺注释。**先对齐既有风格再判断。**

### 技术债标记
- Grep `TODO|FIXME|HACK|XXX|@Deprecated`、被注释掉的代码块、版本不一致（如多处不同库版本）。

### 测试覆盖与质量（重点）
- 关键域是否有集成测试：auth、detection 闭环、notifications、**tenant 隔离（跨租户 404）**。
- 测试质量：断言是否有意义（不止 `status==200`）？覆盖错误/边界路径？
- **seed 即 fixture 脆弱性**：`db/initdb` 整目录挂进 testcontainer，改 seed 会整片红；验证 seed 改动需 `gradle cleanTest`（不在 test 输入会被判 UP-TO-DATE）。检查这类隐性耦合。
- 前端：有无组件/集成测试，还是仅 lint+build。

### 一致性
- 跨组件错误处理、日志、配置风格是否统一。

## 手法
- Grep 技术债标记、重复字符串、`console.log`/裸 `print`。
- 统计：超长文件行数、重复块数、缺测试的关键路径数——给 lead 可排序的量化依据。
- 本机无 node/java/容器 → 静态评估测试文件的存在与断言质量，标「未实际执行」。参考既有 DooD 测试调用法（挂 repo 根 + TESTCONTAINERS_HOST_OVERRIDE）若要尝试运行。

## 协作
缺测试且涉安全 → SendMessage `security-analyst`；结构性坏味 → `architecture-analyst`。完成写 `_workspace/quality_findings.md` 并通知 lead（附技术债热点 top-3）。
