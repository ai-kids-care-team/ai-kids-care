## 1. backend ingest client（TDD）

- [x] 1.1 `ai/tests/test_backend_ingest.py`:create_session/submit_event 的 url/驼峰 body/Bearer header/返回解析/非2xx 抛(injectable mock,无网络,仿 `test_stream_credentials.py`)—— pytest 绿
- [x] 1.2 `ai/src/ai_app/utils/backend_ingest.py`:`create_session(stream_id, model_id, backend_url, token, *, http_post=None)` + `submit_event(...)`;驼峰 JSON body;Bearer;lazy import requests(测试可注入 mock 不依赖 requests)

## 2. event_type 映射 + dedup_key（TDD）

- [x] 2.1 test 覆盖:map_label 全 12 类 + 未知/空/None→OTHER + 大小写规范;build_dedup_key 同输入同 key;severity_from_confidence 边界 —— pytest 绿
- [x] 2.2 `event_type_mapper.map_label`(spec 表,未知→OTHER);`backend_ingest.build_dedup_key(stream_id, onset_epoch)=f"{stream_id}-{int(epoch)}"`(D3);`severity_from_confidence(conf)=max(1,min(5,round(conf*5)))`(D5)

## 3. 改造 stream_live_alert_service（接 ingest + 移除 demo）

- [x] 3.1 stream 连接后 `create_session(STREAM_ID, MODEL_ID, ...)` 存 session_id(失败 log+继续);alarm_on 过 cooldown → `submit_event(...,map_label(target_label),severity_from_confidence(target_prob),now_iso,now_iso,build_dedup_key(...))` try/except 不崩;MODEL_ID env(默认 1)
- [x] 3.2 全文重写移除 Pushover/SMS 直发 + CSV(open_csv_writer/timeline/events/writerow/flush/close)+ pushover/sms import + SMS 函数参数;**persistence 状态机/predictor/decode 循环逐字保留**(test_persistence/test_serving 零回归)

## 4. 配置清理

- [x] 4.1 `ai/.env.example`:删 `PUSHOVER_*`;补 `MODEL_ID`;保留 `JAVA_BACKEND_URL`/`AI_SERVICE_TOKEN`/`STREAM_ID`(SOLAPI/SMS env 原不在 .env.example 中,仅 __main__ os.getenv 读,随移除)

## 5. 验证与收尾（verification-before-completion）

- [x] 5.1 docker `python:3.12` 跑 `PYTHONPATH=src pytest tests/`:**42 passed**(新 ingest/映射/dedup/severity + 既有 persistence/serving/credentials/mask/sample 零回归)
- [ ] 5.2 范围核对:仅 `ai/`(新 backend_ingest/event_type_mapper + 改 stream_live_alert_service + .env.example + 测试);**未改后端/schema**;event ingest 不含 evidence(defer)
- [x] 5.3 code review(**opus** sub-agent):**Ready to merge,无 Blocking**;后端契约逐项对齐、映射表与 spec 一致、dedup 语义、persistence 状态机(diff + test_persistence exec 双重确认)不变、两路径配置完整、demo 移除无悬空引用。follow-up:`notification_title` 未用参数清理、severity 占位语义(D5)、ingest 失败重试缓冲
- [x] 5.4 archive(ai-detection spec delta sync)+ commit develop + push

---

> 纯 AI 端(Python),无后端/schema 改动。evidence(④')、SMS 端口(⑤)、前端(⑥)、ingest 重试缓冲均 follow-up。本机无真实流 → 验证靠 pytest 单元(mock);真实端到端留集成。
