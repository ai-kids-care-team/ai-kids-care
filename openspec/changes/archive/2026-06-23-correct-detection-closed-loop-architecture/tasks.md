## 1. Spec 捕获（纯文档，无代码）

- [x] 1.1 核对 `specs/ai-detection/spec.md` delta：闭环架构改为 AI→后端 REST 内部端点摄入、后端独占写库、dedup_key 由 AI 生成、evidence 行由后端写、LISTEN/NOTIFY 移除（改为 ingest 时推前端）
- [x] 1.2 核对 `specs/notifications/spec.md` delta：staff 即时告警触发器=AI persistence-rule alarm（非 confidence 阈值）、通道 Pushover+SMS+站内+看板、不通知家长
- [x] 1.3 `openspec validate correct-detection-closed-loop-architecture --strict` 通过

## 2. 收尾

- [x] 2.1 合并 develop / push / `/opsx:archive`（sync ai-detection + notifications 主 spec）；archive 后 ai-detection 真相源不再有「AI 直接写 DB / LISTEN-NOTIFY」漂移

---

> 纯 spec/设计捕获：无产品代码、无 schema、无迁移。后续实现各自起 change（见 design.md Migration Plan：① 后端摄入端点+staff 告警 ② 复核工作流 ③ 复核→规则引擎→家长 PUSH ④ AI ingest 客户端 ⑤ SMS 端口+Solapi 适配器 ⑥ 前端看板）。
