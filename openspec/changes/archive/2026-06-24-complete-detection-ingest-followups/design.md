## Context

闭环 ④(`wire-ai-detection-ingest-client`,archived)落地了 AI→后端 ingest 的主干,但留了四处 follow-up,均在 AI 端(`ai/`):

- **evidence**:后端契约已在 `3af66f8`(`wire-detection-evidence-ingest`,archived)就绪 —— `DetectionEventIngestRequest`(`internal` 包)接受可选 `@Valid EvidenceFile evidence`,字段 `{uri, hash, type(EvidenceFileTypeEnum), mimeType(MimeTypeEnum)}`,all-or-nothing,fresh-event 路径写 `event_evidence_files`。但 `backend_ingest.submit_event()` 的 payload **从不带 evidence**,AI 端也没有任何抽帧/存片段/算 hash 的代码。
- **severity**:`backend_ingest.severity_from_confidence()` 是 `round(confidence*5)` clamp 到 `[1,5]`,docstring 标 `interim — see design D5`。调用点 `stream_live_alert_service.py:407` 传的是 `target_prob`(目标类 softmax 概率)。
- **notification_title**:`run_stream_service()` 签名(`stream_live_alert_service.py:183`)收了它,`__main__:527` 赋值、`:554` 传参,但函数体 500+ 行从不读取 —— Pushover/SMS 时代死参数。
- **ingest 失败**:`create_session`/`submit_event` 失败仅 `print("[WARN]...")`,无重试/退避/缓冲(`stream_live_alert_service.py:239-247`、`:418-422`);spec 现状是 "best-effort,失败 log 且不得 crash"。

测试:`ai/tests/test_backend_ingest.py` 已有 injectable mock 模式(`http_post=mock_post`,纯 mock 无网络),docker `python:3.12` + `PYTHONPATH=src pytest tests/` 跑。`conftest.py` stub 了 `torch/transformers/av`。

## Goals / Non-Goals

**Goals:**
- AI 端在 `alarm_on` 时产出本地 `video/mp4` 片段 + SHA-256,随 ingest 发送已就绪的 `evidence` 描述符。
- `severity_from_confidence` 改为已定稿的分档规则(非 interim),修正调用点传参语义。
- 移除死参数 `notification_title`。
- ingest 失败做有界重试 + 退避;耗尽后仍 best-effort 放弃并 log,绝不 crash 流服务。
- 全程 TDD,纯 AI 端,零后端/schema 改动。

**Non-Goals:**
- 后端 evidence DTO/持久化(已完成)。
- 对象存储(S3/MinIO)上传(本期写本地 `file://`)。
- 跨进程持久化失败队列 / 多实例(本期进程内有界重试)。
- evidence 片段保留/清理策略(磁盘增长,记 follow-up)。

## Decisions

### D1. evidence 抽帧 → 本地 mp4 → SHA-256(新模块 `evidence_capture.py`)
新增 `ai/src/ai_app/utils/evidence_capture.py`,提供纯函数式接口便于注入测试:`save_and_hash(frames, out_dir, *, encoder=...) -> (uri, sha256)`。从 `alarm_on` 时的帧缓冲取一段帧,编码为 `mp4` 写到本地目录,返回 `file://` URI 与对内容算的 SHA-256。`backend_ingest.submit_event()` 增加可选 `evidence: dict | None` 参数,非空时塞进 body 的 `evidence` 键。
- **为什么本地 `file://` 而非 S3**:后端只存 URI 字符串 + hash,不读字节;远端存储是独立演进,先打通端到端契约。spec 已写 "upgradeable to s3://"。
- **为什么独立模块**:抽帧/编码依赖(`av`/`ffmpeg`)与 HTTP 客户端解耦;`conftest.py` 已 stub `av`,测试可注入假 encoder 只验 "拿到 frames→产出 (uri,hash) 且 hash 稳定"。
- **失败降级**:capture/hash 抛错时,`submit_event` 不带 evidence 照常发(spec 场景 "Evidence capture failure still submits the event"),绝不因取证失败丢事件。

### D2. severity 分档规则定稿
把 `severity_from_confidence` 改为显式区间分档(单调不减、clamp `[1,5]`、对相同输入稳定),docstring 去掉 interim。拟定区间(confidence ∈ [0,1]):`<0.30→1, <0.50→2, <0.70→3, <0.85→4, ≥0.85→5`(具体阈值在实现时随测试确定,作为 documented rule)。调用点继续传 `target_prob`(驱动告警的目标类概率),并在调用处注释说明语义。
- **为什么分档而非线性 round**:线性 `round(conf*5)` 在边界(如 0.1→1 但 0.9→5、0.5→3)对运营不直观;分档是可解释、可调的业务规则。
- **替代**:也可后端集中算 severity —— 否决,severity 已是 ingest 契约字段且 AI 最懂 confidence 语义,保持 AI 端产出。

### D3. ingest 有界重试 + 退避
在 `backend_ingest` 的 `create_session`/`submit_event` 内做有界重试(默认 3 次)+ 指数退避(默认 0.5s→1s→2s),`sleep` 与 `http_post` 均可注入以便测试零等待。耗尽后吞异常 + log,返回 `None`(维持现有 "session_id 为 None 则跳过 submit" 语义)。
- **为什么进程内有界、不做持久队列**:spec 只要求 "不 crash";持久失败队列是多实例/可靠投递的范畴,与已 defer 的多实例项一致。把 "一次抖动即永久丢" 收窄为 "短抖可恢复" 即达本期目标。
- **退避注入**:重试逻辑接受 `sleeper=time.sleep` 默认参数,测试传 no-op,既验重试次数又不拖慢测试。

### D4. 移除 `notification_title`
删 `run_stream_service` 签名参数、`__main__` 的本地赋值与传参。纯删除,无 spec 影响(实现细节)。

## Risks / Trade-offs

- **取证在告警线程引入 IO/CPU**(抽帧+编码+hash)→ 片段保持短(秒级);capture 失败即降级发无 evidence 事件;不阻塞后续窗口处理。
- **本地片段磁盘增长无清理** → 本期 Non-goal,design 记 follow-up(retention/清理或转 S3)。
- **重试在 ingest 路径增加延迟** → 退避总预算有界(~3.5s 上限)且 ingest 本就在告警后台路径;耗尽即放弃。
- **severity 阈值是产品判断** → 选保守可解释区间,集中在一个函数 + 参数化测试,后续好调。
- **`av`/`ffmpeg` 运行时可用性** → 见 Open Questions;模块对 encoder 做依赖注入,运行时不可用时 capture 失败走降级路径(不丢事件)。

## Migration Plan

无 schema、无后端、无数据迁移。纯 AI 端代码:部署随 AI 服务镜像滚动;evidence/重试均为增量行为,旧 payload(无 evidence)后端仍接受,向后兼容。回滚 = 还原 AI 镜像。

## Open Questions

- evidence 片段的时长/帧数/编码参数(取多少帧、目标码率)—— 实现时按 `frame_buffer` 现状定,先求打通。
- 本地片段落盘目录与生命周期(谁清理、保留多久)—— 本期写 `file://` 临时目录,清理留 follow-up。
- `severity` 分档阈值最终取值 —— 实现时用参数化测试钉死,本设计给出拟定区间。
