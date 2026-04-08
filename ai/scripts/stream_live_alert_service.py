#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Live stream inference service:
- consume one FLV stream URL
- run window-based VideoMAE inference
- apply persistence rule (same core logic as realtime_persistence_demo)
- send pushover alert when alarm turns on
"""
from __future__ import annotations

import csv
import math
import os
import sys
import time
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

import av
import numpy as np
import torch
from transformers import VideoMAEForVideoClassification, VideoMAEImageProcessor

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = PROJECT_ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ai_app.utils.pushover import send_pushover_notification, send_pushover_notifications
from ai_app.utils.sms import build_message_service, parse_recipients, send_sms_batch
from realtime_persistence_demo import (
    frame_time_sec,
    label_for_id,
    label_to_id,
    maybe_downscale_frame,
    resolve_fps,
    safe_log_text,
    sample_frame_indices,
)


@dataclass
class PersistenceState:
    history: deque[tuple[float, int]] = field(default_factory=deque)
    alarm_on: bool = False
    alarm_start_sec: float | None = None


def open_csv_writer(path: Path, fieldnames: list[str]) -> tuple[csv.DictWriter, object]:
    path.parent.mkdir(parents=True, exist_ok=True)
    file_exists = path.exists()
    f = open(path, "a", newline="", encoding="utf-8")
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    if not file_exists:
        writer.writeheader()
        f.flush()
    return writer, f


def detect_black_screen(
        frames_rgb: list[np.ndarray],
        luma_threshold: float,
        std_threshold: float,
) -> tuple[bool, float, float]:
    if not frames_rgb:
        return False, math.nan, math.nan

    mean_list: list[float] = []
    std_list: list[float] = []
    for frame in frames_rgb:
        sampled = frame[::16, ::16, :].astype(np.float32, copy=False)
        luma = (
                0.299 * sampled[..., 0]
                + 0.587 * sampled[..., 1]
                + 0.114 * sampled[..., 2]
        )
        mean_list.append(float(luma.mean()))
        std_list.append(float(luma.std()))

    mean_luma = float(np.mean(mean_list))
    std_luma = float(np.mean(std_list))
    is_black = (mean_luma <= float(luma_threshold)) and (std_luma <= float(std_threshold))
    return is_black, mean_luma, std_luma


def update_persistence_state(
        state: PersistenceState,
        ts_sec: float,
        target_prob: float,
        clip_positive_threshold: float,
        persistence_window_sec: float,
        persistence_hit_ratio: float,
        clear_hit_ratio: float,
        min_history_sec: float,
        min_hits: int,
        window_is_valid: bool = True,
) -> dict:
    history_start_limit = ts_sec - persistence_window_sec
    if window_is_valid:
        is_hit = int(target_prob >= clip_positive_threshold)
        state.history.append((ts_sec, is_hit))
        while state.history and state.history[0][0] < history_start_limit:
            state.history.popleft()
    else:
        is_hit = 0
        while state.history and state.history[0][0] < history_start_limit:
            state.history.popleft()

    history_count = len(state.history)
    hit_count = int(sum(hit for _, hit in state.history))
    hit_ratio = float(hit_count / history_count) if history_count > 0 else 0.0
    history_span_sec = float(state.history[-1][0] - state.history[0][0]) if history_count > 1 else 0.0
    history_ready = history_span_sec >= min_history_sec

    should_turn_on = (
            window_is_valid
            and history_ready
            and hit_count >= min_hits
            and hit_ratio >= persistence_hit_ratio
    )
    should_turn_off = (state.alarm_on and not window_is_valid) or (history_ready and hit_ratio <= clear_hit_ratio)

    event_type = ""
    event_start = math.nan
    event_end = math.nan
    event_duration = math.nan

    if not state.alarm_on and should_turn_on:
        state.alarm_on = True
        state.alarm_start_sec = ts_sec
        event_type = "alarm_on"
    elif state.alarm_on and should_turn_off:
        start_sec = float(state.alarm_start_sec if state.alarm_start_sec is not None else ts_sec)
        state.alarm_on = False
        state.alarm_start_sec = None
        event_type = "alarm_off"
        event_start = start_sec
        event_end = ts_sec
        event_duration = max(0.0, ts_sec - start_sec)

    return {
        "clip_hit": is_hit,
        "rolling_count": history_count,
        "rolling_hit_count": hit_count,
        "rolling_hit_ratio": hit_ratio,
        "history_span_sec": history_span_sec,
        "history_ready": int(history_ready),
        "window_is_valid": int(window_is_valid),
        "alarm_on": int(state.alarm_on),
        "event_type": event_type,
        "event_start_sec": event_start,
        "event_end_sec": event_end,
        "event_duration_sec": event_duration,
    }


def run_stream_service(
        stream_url: str,
        model_dir: Path,
        output_dir: Path,
        target_label: str = "assault",
        window_sec: float = 5.0,
        step_sec: float = 2.0,
        num_frames: int = 16,
        sampling_rate: int = 4,
        max_short_side: int | None = 360,
        decode_thread_type: str | None = "AUTO",
        clip_positive_threshold: float = 0.60,
        persistence_window_sec: float = 60.0,
        persistence_hit_ratio: float = 0.50,
        clear_hit_ratio: float = 0.40,
        min_history_sec: float = 30.0,
        min_hits: int = 8,
        enable_black_screen_gate: bool = True,
        black_luma_threshold: float = 18.0,
        black_std_threshold: float = 8.0,
        notification_title: str = "AI Alert",
        notification_cooldown_sec: float = 120.0,
        enable_sms_batch_notification: bool = False,
        sms_api_key: str = "",
        sms_api_secret: str = "",
        sms_sender: str = "",
        sms_recipients: list[str] | None = None,
        reconnect_wait_sec: float = 3.0,
        max_runtime_sec: float | None = None,
        max_eval_windows: int | None = None,
        log_every_n_windows: int = 20,
) -> None:
    if not model_dir.exists():
        raise FileNotFoundError(f"model_dir not found: {model_dir}")
    if not stream_url.strip():
        raise ValueError("stream_url is empty.")

    output_dir.mkdir(parents=True, exist_ok=True)
    timeline_path = output_dir / "stream_timeline.csv"
    events_path = output_dir / "stream_alarm_events.csv"

    timeline_fields = [
        "service_eval_index",
        "connection_index",
        "eval_index_in_connection",
        "eval_frame_idx",
        "ts_sec",
        "pred_label",
        "pred_conf",
        "target_label",
        "target_prob",
        "clip_hit",
        "rolling_count",
        "rolling_hit_count",
        "rolling_hit_ratio",
        "history_span_sec",
        "history_ready",
        "alarm_on",
    ]
    event_fields = [
        "event_index",
        "connection_index",
        "event_type",
        "ts_sec",
        "alarm_start_sec",
        "alarm_end_sec",
        "duration_sec",
        "target_prob",
        "rolling_hit_ratio",
        "rolling_hit_count",
        "rolling_count",
        "message",
    ]

    timeline_writer, timeline_file = open_csv_writer(timeline_path, timeline_fields)
    event_writer, event_file = open_csv_writer(events_path, event_fields)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cuda":
        torch.backends.cudnn.benchmark = True

    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    processor = VideoMAEImageProcessor.from_pretrained(model_dir)
    model.to(device)
    model.eval()
    target_id = label_to_id(model, target_label)

    print("\n===== Live Stream Alert Service =====")
    print(f"stream_url: {safe_log_text(stream_url)}")
    print(f"model_dir: {safe_log_text(model_dir)}")
    print(f"output_dir: {safe_log_text(output_dir)}")
    print(f"device: {device}")
    print(f"target_label: {target_label}")
    print(f"window_sec: {window_sec}")
    print(f"step_sec: {step_sec}")
    print(f"num_frames: {num_frames}")
    print(f"sampling_rate: {sampling_rate}")
    print(f"max_short_side: {max_short_side}")
    print(f"decode_thread_type: {decode_thread_type}")
    print(f"clip_positive_threshold: {clip_positive_threshold}")
    print(f"persistence_window_sec: {persistence_window_sec}")
    print(f"persistence_hit_ratio: {persistence_hit_ratio}")
    print(f"clear_hit_ratio: {clear_hit_ratio}")
    print(f"min_history_sec: {min_history_sec}")
    print(f"min_hits: {min_hits}")
    print(f"enable_black_screen_gate: {enable_black_screen_gate}")
    print(f"black_luma_threshold: {black_luma_threshold}")
    print(f"black_std_threshold: {black_std_threshold}")
    print(f"notification_cooldown_sec: {notification_cooldown_sec}")
    print(f"enable_sms_batch_notification: {enable_sms_batch_notification}")
    print(f"sms_recipient_count: {len(sms_recipients or [])}")
    print(f"timeline_csv: {safe_log_text(timeline_path)}")
    print(f"event_csv: {safe_log_text(events_path)}")

    sms_service = None
    effective_sms_recipients: list[str] = []
    if enable_sms_batch_notification:
        effective_sms_recipients = [str(x).strip() for x in (sms_recipients or []) if str(x).strip()]
        if not sms_api_key or not sms_api_secret or not sms_sender or not effective_sms_recipients:
            print("[WARN] SMS batch notification is enabled but config is incomplete. SMS will be skipped.")
        else:
            try:
                sms_service = build_message_service(api_key=sms_api_key, api_secret=sms_api_secret)
            except Exception as sms_init_error:
                print(
                    "[WARN] Failed to initialize SMS service. SMS will be skipped. "
                    f"detail={safe_log_text(type(sms_init_error).__name__ + ': ' + str(sms_init_error))}"
                )

    service_start_wall = time.monotonic()
    service_eval_index = 0
    event_index = 0
    connection_index = 0
    last_notification_wall = -1e18
    state = PersistenceState()

    try:
        while True:
            if max_runtime_sec is not None and (time.monotonic() - service_start_wall) >= float(max_runtime_sec):
                print("[INFO] Reached max_runtime_sec. Service stops.")
                break
            if max_eval_windows is not None and service_eval_index >= int(max_eval_windows):
                print("[INFO] Reached max_eval_windows. Service stops.")
                break

            connection_index += 1
            container = None
            try:
                print(f"[INFO] Connecting stream (#{connection_index}) ...")
                container = av.open(stream_url)
                print(f"[INFO] Stream opened (#{connection_index}).")
                if not container.streams.video:
                    raise ValueError("No video stream found in input stream URL.")

                stream = container.streams.video[0]
                if decode_thread_type:
                    stream.thread_type = decode_thread_type
                    print(f"[INFO] stream.thread_type={decode_thread_type}")

                fps = resolve_fps(stream)
                window_frames_required = max(
                    1,
                    int(round(window_sec * fps)),
                    int(num_frames * sampling_rate),
                )
                step_frames = max(1, int(round(step_sec * fps)))
                keep_window_sec = max(window_sec + step_sec + 3.0, window_sec + 1.0)

                frame_buffer: deque[tuple[int, float, np.ndarray]] = deque()
                frame_idx = -1
                eval_index_in_connection = 0
                next_eval_frame_idx = window_frames_required - 1

                print(
                    f"[INFO] Connected (#{connection_index}). fps={fps:.4f}, "
                    f"window_frames={window_frames_required}, step_frames={step_frames}"
                )

                for frame in container.decode(video=0):
                    frame_idx += 1
                    ts_sec = frame_time_sec(frame)
                    if ts_sec is None:
                        ts_sec = frame_idx / fps

                    frame = maybe_downscale_frame(frame, max_short_side=max_short_side)
                    frame_rgb = frame.to_ndarray(format="rgb24")
                    frame_buffer.append((frame_idx, float(ts_sec), frame_rgb))

                    while frame_buffer and (ts_sec - frame_buffer[0][1]) > keep_window_sec:
                        frame_buffer.popleft()

                    if frame_idx < next_eval_frame_idx:
                        continue

                    eval_index_in_connection += 1
                    service_eval_index += 1

                    eval_ts_sec = float(ts_sec)
                    window_start_sec = max(0.0, eval_ts_sec - float(window_sec))
                    window_frames = [item[2] for item in frame_buffer if item[1] >= window_start_sec]
                    if len(window_frames) <= 0:
                        next_eval_frame_idx += step_frames
                        continue

                    indices = sample_frame_indices(
                        total_frames=len(window_frames),
                        num_frames=num_frames,
                        sampling_rate=sampling_rate,
                    )
                    sampled_frames = [window_frames[i] for i in indices]
                    is_black_screen = False
                    black_luma = math.nan
                    black_std = math.nan
                    if enable_black_screen_gate:
                        is_black_screen, black_luma, black_std = detect_black_screen(
                            frames_rgb=sampled_frames,
                            luma_threshold=black_luma_threshold,
                            std_threshold=black_std_threshold,
                        )

                    inputs = processor(sampled_frames, return_tensors="pt")
                    pixel_values = inputs["pixel_values"].to(device, non_blocking=(device == "cuda"))

                    with torch.inference_mode():
                        outputs = model(pixel_values=pixel_values)
                        probs = torch.softmax(outputs.logits, dim=-1)[0].detach().cpu().numpy()

                    pred_id = int(np.argmax(probs))
                    pred_label = label_for_id(model, pred_id)
                    pred_conf = float(probs[pred_id])
                    target_prob = float(probs[target_id])

                    persistence = update_persistence_state(
                        state=state,
                        ts_sec=eval_ts_sec,
                        target_prob=target_prob,
                        clip_positive_threshold=clip_positive_threshold,
                        persistence_window_sec=persistence_window_sec,
                        persistence_hit_ratio=persistence_hit_ratio,
                        clear_hit_ratio=clear_hit_ratio,
                        min_history_sec=min_history_sec,
                        min_hits=min_hits,
                        window_is_valid=(not is_black_screen) if enable_black_screen_gate else True,
                    )

                    timeline_writer.writerow({
                        "service_eval_index": service_eval_index,
                        "connection_index": connection_index,
                        "eval_index_in_connection": eval_index_in_connection,
                        "eval_frame_idx": frame_idx,
                        "ts_sec": eval_ts_sec,
                        "pred_label": pred_label,
                        "pred_conf": pred_conf,
                        "target_label": target_label,
                        "target_prob": target_prob,
                        "clip_hit": persistence["clip_hit"],
                        "rolling_count": persistence["rolling_count"],
                        "rolling_hit_count": persistence["rolling_hit_count"],
                        "rolling_hit_ratio": persistence["rolling_hit_ratio"],
                        "history_span_sec": persistence["history_span_sec"],
                        "history_ready": persistence["history_ready"],
                        "alarm_on": persistence["alarm_on"],
                    })
                    if service_eval_index % max(1, int(log_every_n_windows)) == 0:
                        print(
                            "[PRED] "
                            f"idx={service_eval_index}, "
                            f"conn={connection_index}, "
                            f"ts={eval_ts_sec:.2f}s, "
                            # f"pred={pred_label}, ",
                            f"pred={pred_label}{"(퐁력)" if pred_label == "assault" else "(정상)"}, ",
                            f"conf={pred_conf:.4f}, "
                            f"target_prob={target_prob:.4f}, "
                            f"hit={int(persistence['clip_hit'])}, "
                            f"roll={int(persistence['rolling_hit_count'])}/{int(persistence['rolling_count'])} "
                            f"({float(persistence['rolling_hit_ratio']):.4f}), "
                            f"valid={int(persistence['window_is_valid'])}, "
                            f"black={int(is_black_screen)}, "
                            f"black_luma={black_luma:.2f}, "
                            f"black_std={black_std:.2f}, "
                            f"alarm_on={int(persistence['alarm_on'])}"
                        )
                        timeline_file.flush()

                    event_type = str(persistence["event_type"])
                    if event_type:
                        event_index += 1
                        event_message = (
                            f"{event_type} at {eval_ts_sec:.2f}s, target_prob={target_prob:.4f}, "
                            f"rolling_hit_ratio={float(persistence['rolling_hit_ratio']):.4f}, "
                            f"rolling_hit_count={int(persistence['rolling_hit_count'])}/"
                            f"{int(persistence['rolling_count'])}"
                        )
                        event_writer.writerow({
                            "event_index": event_index,
                            "connection_index": connection_index,
                            "event_type": event_type,
                            "ts_sec": eval_ts_sec,
                            "alarm_start_sec": persistence["event_start_sec"],
                            "alarm_end_sec": persistence["event_end_sec"],
                            "duration_sec": persistence["event_duration_sec"],
                            "target_prob": target_prob,
                            "rolling_hit_ratio": persistence["rolling_hit_ratio"],
                            "rolling_hit_count": persistence["rolling_hit_count"],
                            "rolling_count": persistence["rolling_count"],
                            "message": event_message,
                        })
                        event_file.flush()
                        print(f"[INFO] {event_message}")

                        if event_type == "alarm_on":
                            now_wall = time.monotonic()
                            if (now_wall - last_notification_wall) >= float(notification_cooldown_sec):
                                alert_message = (
                                    f"[{target_label}] alarm_on at {eval_ts_sec:.2f}s\n"
                                    f"prob={target_prob:.4f}, "
                                    f"hit_ratio={float(persistence['rolling_hit_ratio']):.4f}, "
                                    f"hits={int(persistence['rolling_hit_count'])}/"
                                    f"{int(persistence['rolling_count'])}"
                                )
                                try:
                                    # send_pushover_notification(
                                    #     notification_title,
                                    #     alert_message,
                                    #     sound="alien"
                                    # )
                                    send_pushover_notifications(
                                        notification_title,
                                        alert_message,
                                        sound="alien",
                                        user_keys=["uqrthzq6a6ha3dpnp383ceoj46i9kq", "ubijdryhdmyxk2z89n982hz5yj6utg"]

                                    )
                                except Exception as push_error:
                                    print(
                                        "[WARN] send_pushover_notification failed: "
                                        f"{safe_log_text(type(push_error).__name__ + ': ' + str(push_error))}"
                                    )

                                if sms_service is not None:
                                    try:
                                        sms_results = send_sms_batch(
                                            message_service=sms_service,
                                            sender=sms_sender,
                                            recipients=effective_sms_recipients,
                                            text=alert_message.replace("\n", " | "),
                                        )
                                        sms_success = int(sum(1 for x in sms_results if x.get("ok")))
                                        print(
                                            f"[INFO] SMS batch send done: success={sms_success}/{len(sms_results)}"
                                        )
                                    except Exception as sms_error:
                                        print(
                                            "[WARN] send_sms_batch failed: "
                                            f"{safe_log_text(type(sms_error).__name__ + ': ' + str(sms_error))}"
                                        )

                                last_notification_wall = now_wall
                            else:
                                print("[INFO] Notification cooldown active. Skip push.")

                    if max_eval_windows is not None and service_eval_index >= int(max_eval_windows):
                        break
                    if max_runtime_sec is not None and (
                            time.monotonic() - service_start_wall
                    ) >= float(max_runtime_sec):
                        break

                    next_eval_frame_idx += step_frames

                print(
                    f"[WARN] Stream decode loop ended (connection #{connection_index}). "
                    f"Reconnect after {reconnect_wait_sec}s."
                )
            except KeyboardInterrupt:
                raise
            except Exception as e:
                print(
                    f"[WARN] Stream connection #{connection_index} failed: "
                    f"{safe_log_text(type(e).__name__ + ': ' + str(e))}. "
                    f"Reconnect after {reconnect_wait_sec}s."
                )
            finally:
                if container is not None:
                    try:
                        container.close()
                    except Exception:
                        pass

            # Reset rolling history after disconnect/reconnect to avoid cross-session contamination.
            state = PersistenceState()
            time.sleep(max(0.0, float(reconnect_wait_sec)))
    finally:
        timeline_file.flush()
        event_file.flush()
        timeline_file.close()
        event_file.close()


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parent.parent

    # Stream + model
    stream_url = "http://www.ai-kids-care.asia:8082/live/livestream.flv"
    model_dir = project_root / "outputs" / "01_assault_videomae_baseline" / "best_model"
    output_dir = project_root / "outputs" / "predictions" / "stream_live_service"
    target_label = "assault"

    # Sliding inference setup
    window_sec = 5.0
    step_sec = 2.0
    num_frames = 16
    sampling_rate = 4
    max_short_side = 360
    decode_thread_type = None

    # Persistence setup (single parameter set from sweep result)
    clip_positive_threshold = 0.60
    persistence_hit_ratio = 0.50
    persistence_window_sec = 60.0
    clear_hit_ratio = 0.40
    min_history_sec = 30.0
    min_hits = math.ceil((math.floor(min_history_sec / step_sec) + 1) * persistence_hit_ratio)
    enable_black_screen_gate = True
    black_luma_threshold = 18.0
    black_std_threshold = 8.0

    # Service behavior
    notification_title = "AI Kids Care Alert"
    notification_cooldown_sec = 120.0
    enable_sms_batch_notification = True
    sms_api_key = os.getenv("SOLAPI_API_KEY", "")
    sms_api_secret = os.getenv("SOLAPI_API_SECRET", "")
    sms_sender = os.getenv("SMS_DEFAULT_SENDER", "")
    sms_recipients = parse_recipients(os.getenv("SMS_DEFAULT_RECIPIENTS", ""))
    reconnect_wait_sec = 3.0
    max_runtime_sec = None  # set seconds for local dry run, e.g. 600
    max_eval_windows = None  # set for quick test, e.g. 100
    log_every_n_windows = 1

    run_stream_service(
        stream_url=stream_url,
        model_dir=model_dir.resolve(),
        output_dir=output_dir.resolve(),
        target_label=target_label,
        window_sec=window_sec,
        step_sec=step_sec,
        num_frames=num_frames,
        sampling_rate=sampling_rate,
        max_short_side=max_short_side,
        decode_thread_type=decode_thread_type,
        clip_positive_threshold=clip_positive_threshold,
        persistence_window_sec=persistence_window_sec,
        persistence_hit_ratio=persistence_hit_ratio,
        clear_hit_ratio=clear_hit_ratio,
        min_history_sec=min_history_sec,
        min_hits=min_hits,
        enable_black_screen_gate=enable_black_screen_gate,
        black_luma_threshold=black_luma_threshold,
        black_std_threshold=black_std_threshold,
        notification_title=notification_title,
        notification_cooldown_sec=notification_cooldown_sec,
        enable_sms_batch_notification=enable_sms_batch_notification,
        sms_api_key=sms_api_key,
        sms_api_secret=sms_api_secret,
        sms_sender=sms_sender,
        sms_recipients=sms_recipients,
        reconnect_wait_sec=reconnect_wait_sec,
        max_runtime_sec=max_runtime_sec,
        max_eval_windows=max_eval_windows,
        log_every_n_windows=log_every_n_windows,
    )
