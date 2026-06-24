## 1. severity 分档定稿(最小风险先行)

- [x] 1.1 写参数化测试(`ai/tests/test_backend_ingest.py`):覆盖每个区间边界 confidence → 期望 severity,断言单调不减、clamp `[1,5]`、对相同输入稳定(先看失败)
- [x] 1.2 改 `backend_ingest.severity_from_confidence` 为定稿分档规则,docstring 去掉 interim;测试转绿
- [x] 1.3 在调用点 `stream_live_alert_service.py`(原 :407)补注释说明传 `target_prob` 的语义,确认传参正确

## 2. 移除死参数 notification_title

- [x] 2.1 删 `run_stream_service` 签名里的 `notification_title`(原 :183)+ `__main__` 的赋值(原 :527)与传参(原 :554)
- [x] 2.2 加/调整一个 `run_stream_service` 级别冒烟测试:不传 `notification_title` 仍能正常构造/跑通(无 NameError、无残留引用),grep 确认全仓无 `notification_title` 残留

## 3. evidence 抽帧 + hash 工具

- [x] 3.1 写测试(新 `ai/tests/test_evidence_capture.py`):注入假 encoder,`save_and_hash(frames, out_dir)` 返回 `(file:// uri, sha256)`;相同 frames → 相同 hash;不同内容 → 不同 hash;encoder 抛错时函数抛可识别异常(先看失败)
- [x] 3.2 实现 `ai/src/ai_app/utils/evidence_capture.py`:取帧→编码为本地 `mp4`→对写出字节算 SHA-256→返回 `(file:// uri, hash)`;encoder 依赖注入(`av`/ffmpeg 通过参数传入)
- [x] 3.3 测试转绿

## 4. evidence 随 ingest 发送

- [x] 4.1 写测试(`test_backend_ingest.py`):`submit_event(..., evidence={uri,hash,type,mimeType})` 时 mock post 收到的 body 含 `evidence` 且四字段齐;`evidence=None` 时 body 不含 `evidence` 键(先看失败)
- [x] 4.2 给 `backend_ingest.submit_event` 加可选 `evidence` 参数并并入 body;测试转绿
- [x] 4.3 在 `stream_live_alert_service.py` 的 `alarm_on` 分支接入:调 `evidence_capture.save_and_hash`(best-effort)→ 成功则以 `{uri, hash, type:"VIDEO", mimeType:"video/mp4"}` 传给 `submit_event`;capture 失败则不带 evidence 照常发(降级,不丢事件)
- [x] 4.4 写测试覆盖降级路径:capture 抛错时 `submit_event` 仍被调用且 `evidence` 为 None

## 5. ingest 有界重试 + 退避

- [x] 5.1 写测试(`test_backend_ingest.py`):mock post 前 N-1 次非 2xx、第 N 次 200 → 最终成功(注入 no-op sleeper,断言重试次数);mock 全非 2xx → 返回 None 且不抛、不阻塞(先看失败)
- [x] 5.2 在 `create_session`/`submit_event` 内实现有界重试(默认 3 次)+ 指数退避,`sleeper`/`http_post` 可注入;耗尽吞异常 + log + 返回 None(维持 None→跳过语义)
- [x] 5.3 测试转绿;确认 `stream_live_alert_service.py` 失败路径(原 :239-247、:418-422)行为不回退(仍不 crash、继续后续窗口)

## 6. 验证收口

- [x] 6.1 docker `python:3.12` 跑全套 `PYTHONPATH=src pytest tests/` 全绿(参考 backend/AI DooD 调用法)
- [x] 6.2 自检:无 `notification_title` 残留、severity docstring 无 "interim"、`submit_event` evidence 向后兼容(无 evidence 的旧调用仍可用)、重试不改变 best-effort 不 crash 语义
