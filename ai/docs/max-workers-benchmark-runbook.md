# MAX_WORKERS 容量基准 Runbook

> shard-live-detection-deployments design D4 / tasks 4.1–4.2。**本文档不含实测数值**——4.3
> 端到端实测取值留待维护者在目标 GPU 机执行（本 change 无 GPU 环境）。跑完后把本文件的
> 「实测记录」表格填上，作为该型号 GPU 的 `MAX_WORKERS`（= claim 的 `capacity`）依据。

## 为什么需要基准

方案 A（design D1）每个 worker 各自 `from_pretrained(...).to(device)` 加载一份模型副本 + 独立
CUDA context，显存是硬约束；且推理延迟/丢帧会随并发 worker 数增加而劣化。`MAX_WORKERS` 无法从
理论显存除法直接算准（CUDA context 开销、PyAV 解码内存、碎片化都不是常数），必须在目标机器上
用真实/回放视频跑一遍递增实验来定。

## 前置条件

- 目标 GPU 主机已装好 NVIDIA 驱动 + Container Toolkit（`nvidia-smi` 能跑、`docker run --gpus all`
  能过）。
- 已按 `ai/docker-compose.gpu.yml` + `--profile live` 起栈（见该文件顶部注释的命令）；
  `DEPLOYMENT_ID` / `AI_SERVICE_TOKEN` / `JAVA_BACKEND_URL` 均已配置。
- 至少 N_max（预期上限，例如 8）路可用的摄像头流或回放源——真实 RTSP 流或本地回放服务均可，只要
  supervisor 能通过 claim 认领到它们并各自拉起 worker。
- `ai/scripts/benchmark_max_workers.py` 可在该机器上直接跑（仅需 Python 标准库 + `nvidia-smi`
  在 PATH，不需要本仓库任何 ML 依赖）。

## 步骤

对候选 `N = 1, 2, 3, ...`（每次递增，直至判定失败或显存耗尽）依次执行：

1. 把该栈的 `MAX_WORKERS` 设为 `N`（`.env` 或 compose 环境变量），重启
   `ai-live-supervisor` 容器，确认 `docker logs` 里看到 `N` 个 worker 被 claim/reconcile 拉起
   （`[INFO] started detection worker for stream ...` 出现 N 次）。
2. 让流保持正常输入（真实摄像头或回放）至少覆盖一段「安静 + 触发告警」的混合窗口。
3. 在另一终端跑采样脚本（一次跑一个 N，标签即 N 值）：
   ```bash
   python ai/scripts/benchmark_max_workers.py \
     --label "N=${N}" \
     --duration-sec 300 \
     --interval-sec 5 \
     --output-csv ai/docs/benchmarks/max_workers_$(hostname).csv
   ```
4. 采样期间同时记录（脚本不采集这两项，需人工/日志核对）：
   - **推理延迟**：从告警起始帧到该流对应 worker 完成一次推理窗口判定的耗时（看 worker 日志时间
     戳，或 `stream_live_alert_service` 已有的窗口处理耗时打点）。
   - **丢帧**：PyAV 解码侧是否出现跳帧/掉线重连（worker 日志里的重连/异常次数）。
5. 采样结束后检视该 N 的 CSV 行：`memory.used / memory.total`、`utilization.gpu`。

## 判定规则（design D4）

选「利用率高 + 显存留 ~15% headroom + 延迟不击穿实时性」的**最大** N：

- **显存 headroom**：`memory.used / memory.total` 的峰值应 **≤ 85%**（即预留 ~15% 应对突发/碎片化）。
  超过 85% 的第一个 N 之前一档即为显存侧上限。
- **利用率**：`utilization.gpu` 应显著 > 0（避免选一个远低于饱和、还有大量余量却因为其它原因
  （如 CPU 解码瓶颈）没被使用的 N）；若加一档 N 后利用率几乎不再上升但显存/延迟已经在劣化，说明
  已过拐点。
- **延迟**：单窗口推理延迟不能击穿告警实时性要求（closed-loop 的价值前提——检测到复核的时延要在
  可接受范围内，具体阈值由园所侧现场经验定，无 change 内强制数字）。
- 三者都满足的**最大** N 即为该型号 GPU 的 `MAX_WORKERS`。若三者在同一 N 同时失守（显存溢出 且
  延迟劣化），退回上一档。

## 记入之处

判定出的 N：
1. 写入该型号 GPU 机器的部署 `.env`（`MAX_WORKERS=<N>`），供 `docker-compose.yml` /
   `docker-compose.gpu.yml` 使用（也即 claim 请求的 `capacity`）。
2. 在下面「实测记录」表格追加一行（型号、日期、N、备注）。

## 实测记录

| GPU 型号 | 日期 | 判定 N (`MAX_WORKERS`) | 备注 |
|----------|------|------------------------|------|
| _(留空——待维护者在目标机执行 4.3)_ | | | |

## 何时改用方案 B（design Non-Goals / Risk「单 GPU 密度低」）

若某型号 GPU 判定出的 N 过低（每卡承载流数不经济），把「实测密度 vs GPU 成本」记入 design OQ-4
的触发讨论——方案 B（解码器 + 共享批推理池）是独立 follow-up，不在本 change 范围。
