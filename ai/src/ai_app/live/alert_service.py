#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Live stream inference service (closed-loop step ④):
- consume one FLV/RTSP stream URL
- run window-based VideoMAE inference
- apply persistence rule (same core logic as realtime_persistence_demo)
- on alarm_on, submit a detection event to the Java backend internal ingest endpoint
  (the backend is the sole writer of the detection tables and dispatches staff alerts).

The legacy Pushover/SMS direct-dispatch and the local CSV outputs have been removed — detection
results now flow only through the backend ingest endpoints (ADR-0015 V1).

ARC-02 (harden-ai-serving, C3): this module used to be the standalone script
``scripts/stream_live_alert_service.py``. It now lives inside the ``ai_app`` package so
``ai_app.supervisor`` can load ``run_stream_service`` via a normal (lazy, function-scoped)
package import instead of ``importlib`` file-path loading. ``scripts/stream_live_alert_service.py``
is now a thin shim that re-exports from here, so the legacy
``python scripts/stream_live_alert_service.py`` entrypoint keeps working unchanged.
"""
from __future__ import annotations

import math
import os
import re
import sys
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

import av
import numpy as np
import torch
from transformers import VideoMAEForVideoClassification, VideoMAEImageProcessor

# ai/src/ai_app/live/alert_service.py -> parents[0]=live, [1]=ai_app, [2]=src, [3]=ai (project root)
PROJECT_ROOT = Path(__file__).resolve().parents[3]
# realtime_persistence_demo.py lives in ai/scripts/ and is not part of the ai_app package (it is
# itself a standalone script, imported here the same way the pre-ARC-02 script did — via
# sys.path — rather than duplicated into the package).
SCRIPTS_DIR = PROJECT_ROOT / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from ai_app.utils import backend_ingest
from ai_app.utils.alarm_event import build_alarm_event_params
from ai_app.utils.evidence_capture import EvidenceCaptureError, save_and_hash
from realtime_persistence_demo import (
    frame_time_sec,
    label_for_id,
    label_to_id,
    maybe_downscale_frame,
    resolve_fps,
    safe_log_text,
    sample_frame_indices,
)


def mask_url_credentials(url: str) -> str:
    """Replace userinfo (user:password@) in a URL with ***:***@ for safe logging.

    Uses a regex so that no urllib parse/unparse round-trip can alter query
    strings or path components.  Returns the original string unchanged if no
    userinfo pattern is detected.
    """
    return re.sub(r"(://)[^@/]+@", r"\1***:***@", url)


@dataclass
class PersistenceState:
    history: deque[tuple[float, int]] = field(default_factory=deque)
    alarm_on: bool = False
    alarm_start_sec: float | None = None
    # Wall-clock instant the current alarm window began, captured at the alarm_on transition (D4).
    # Drives the ingest dedupKey + startTime so a debounce/re-trigger of the same alarm is stable.
    alarm_onset_wall: datetime | None = None


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
        notification_cooldown_sec: float = 120.0,
        stream_id: str = "",
        model_id: int = 1,
        java_backend_url: str = "",
        ai_service_token: str = "",
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

    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cuda":
        torch.backends.cudnn.benchmark = True

    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    processor = VideoMAEImageProcessor.from_pretrained(model_dir)
    model.to(device)
    model.eval()
    target_id = label_to_id(model, target_label)

    print("\n===== Live Stream Alert Service =====")
    print(f"stream_url: {safe_log_text(mask_url_credentials(stream_url))}")
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
    print(f"stream_id: {stream_id}")
    print(f"backend_ingest: {'enabled' if (java_backend_url and ai_service_token) else 'disabled'}")

    # ④: create one detection session for this stream run (backend is the sole writer; it derives
    # kindergarten/camera from the stream). Best-effort — a failure must not stop the stream service.
    session_id = None
    if java_backend_url and ai_service_token:
        try:
            session_id = backend_ingest.create_session(stream_id, model_id, java_backend_url, ai_service_token)
            print(f"[INFO] Detection session created: session_id={session_id}")
        except Exception as session_error:
            print(
                "[WARN] create_session failed; detection events will be skipped this run. "
                f"detail={safe_log_text(type(session_error).__name__ + ': ' + str(session_error))}"
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

                _fb_maxlen = max(200, int(math.ceil(keep_window_sec * fps * 1.5)))
                frame_buffer: deque[tuple[int, float, np.ndarray]] = deque(maxlen=_fb_maxlen)
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

                    if service_eval_index % max(1, int(log_every_n_windows)) == 0:
                        print(
                            "[PRED] "
                            f"idx={service_eval_index}, "
                            f"conn={connection_index}, "
                            f"ts={eval_ts_sec:.2f}s, "
                            f"pred={pred_label}{"(폭력)" if pred_label == "assault" else "(정상)"}, "
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

                    event_type = str(persistence["event_type"])
                    if event_type:
                        event_index += 1
                        event_message = (
                            f"{event_type} at {eval_ts_sec:.2f}s, target_prob={target_prob:.4f}, "
                            f"rolling_hit_ratio={float(persistence['rolling_hit_ratio']):.4f}, "
                            f"rolling_hit_count={int(persistence['rolling_hit_count'])}/"
                            f"{int(persistence['rolling_count'])}"
                        )
                        print(f"[INFO] {event_message}")

                        if event_type == "alarm_on":
                            # D4: capture the wall-clock alarm window at the transition instant.
                            # The alarm crosses the persistence threshold *now* (window end); the
                            # window began when the rolling evidence started, i.e. now minus the
                            # current history span. Both the dedupKey and startTime are derived from
                            # this captured onset (NOT the submission time), so a debounce/re-trigger
                            # of the same alarm yields the same key and the event carries a real,
                            # non-zero time window. Stored on state so a same-episode re-submit reuses
                            # it; cleared on alarm_off / reconnect (fresh PersistenceState).
                            transition_wall = datetime.now(timezone.utc)
                            history_span_sec = float(persistence["history_span_sec"])
                            state.alarm_onset_wall = transition_wall - timedelta(seconds=history_span_sec)

                            now_wall = time.monotonic()
                            if (now_wall - last_notification_wall) >= float(notification_cooldown_sec):
                                if session_id is not None:
                                    # Best-effort evidence capture (design D1): encode a short clip
                                    # from the current alarm window's frames and hash it. If capture
                                    # fails for any reason we submit the event WITHOUT evidence rather
                                    # than drop it (downgrade, never lose the event).
                                    evidence_descriptor = None
                                    try:
                                        evidence_dir = output_dir / "evidence"
                                        clip_uri, clip_hash = save_and_hash(window_frames, str(evidence_dir))
                                        evidence_descriptor = {
                                            "uri": clip_uri,
                                            "hash": clip_hash,
                                            "type": "VIDEO",
                                            "mimeType": "video/mp4",
                                        }
                                    except EvidenceCaptureError as capture_error:
                                        print(
                                            "[WARN] evidence capture failed; submitting event without it: "
                                            f"{safe_log_text(str(capture_error))}"
                                        )
                                    try:
                                        # D4: dedupKey + startTime from the captured onset, endTime
                                        # from the transition instant. severity is derived from
                                        # target_prob (the target-class softmax probability driving
                                        # this alarm), not pred_conf. event_type/severity/dedupKey are
                                        # built by the pure helper (unit-tested in isolation).
                                        event_params = build_alarm_event_params(
                                            stream_id=stream_id,
                                            target_label=target_label,
                                            target_prob=target_prob,
                                            alarm_onset=state.alarm_onset_wall,
                                            window_end=transition_wall,
                                        )
                                        result = backend_ingest.submit_event(
                                            session_id,
                                            event_params["event_type"],
                                            event_params["severity"],
                                            event_params["confidence"],
                                            event_params["start_time"],
                                            event_params["end_time"],
                                            event_params["dedup_key"],
                                            java_backend_url,
                                            ai_service_token,
                                            evidence=evidence_descriptor,
                                        )
                                        print(
                                            "[INFO] Detection event ingested: "
                                            f"eventId={result.get('eventId')}, duplicate={result.get('duplicate')}"
                                        )
                                    except Exception as ingest_error:
                                        print(
                                            "[WARN] submit_event failed: "
                                            f"{safe_log_text(type(ingest_error).__name__ + ': ' + str(ingest_error))}"
                                        )
                                else:
                                    print("[WARN] No detection session; skipping event ingest.")

                                last_notification_wall = now_wall
                            else:
                                print("[INFO] Notification cooldown active. Skip ingest.")

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
                safe_msg = mask_url_credentials(type(e).__name__ + ": " + str(e))
                print(
                    f"[WARN] Stream connection #{connection_index} failed: "
                    f"{safe_log_text(safe_msg)}. "
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
        print("[INFO] Stream alert service stopped.")


def main() -> None:  # pragma: no cover — process entrypoint, exercised by integration only
    """Standalone-run entrypoint (ADR-0026 D3 independent-test path / legacy invocation).

    ARC-02: this body used to run directly under ``if __name__ == "__main__":`` in the
    standalone script. It is now a callable ``main()`` so both this module (if ever run
    directly) and the ``scripts/stream_live_alert_service.py`` shim can invoke the exact
    same logic.
    """
    project_root = PROJECT_ROOT

    # Stream + model
    # ADR-0026 Phase 3 (D3): prefer STREAM_ID + credential endpoint; fall back to STREAM_URL.
    stream_id = os.getenv("STREAM_ID", "")
    stream_url_fallback = os.getenv("STREAM_URL", "")

    if stream_id:
        # Phase 3 (ADR-0026 D3): call Java credential endpoint to obtain stream URL.
        java_backend_url = os.getenv("JAVA_BACKEND_URL", "http://backend:8080")
        ai_service_token = os.getenv("AI_SERVICE_TOKEN", "")
        if not ai_service_token:
            raise ValueError("AI_SERVICE_TOKEN must be set when STREAM_ID is used")
        from ai_app.utils.stream_credentials import (  # noqa: PLC0415 (lazy import)
            build_stream_url,
            fetch_stream_credentials,
        )
        cred = fetch_stream_credentials(stream_id, java_backend_url, ai_service_token)
        stream_url = build_stream_url(cred)
        # Log only host:port to avoid leaking credentials in the URL userinfo.
        # _parsed.netloc includes userinfo (user:pass@host:port); use hostname+port instead.
        from urllib.parse import urlparse as _urlparse  # noqa: PLC0415
        _parsed = _urlparse(cred.get("sourceUrl", ""))
        _host_port = f"{_parsed.hostname}:{_parsed.port}" if _parsed.hostname else ""
        print(f"[INFO] Stream credentials fetched for stream_id={stream_id}, host={_host_port}")
    elif stream_url_fallback:
        # Legacy fallback: direct STREAM_URL (ADR-0026 D3 independent-test path).
        stream_url = stream_url_fallback
        java_backend_url = os.getenv("JAVA_BACKEND_URL", "http://backend:8080")
        ai_service_token = os.getenv("AI_SERVICE_TOKEN", "")
    else:
        raise ValueError("Either STREAM_ID or STREAM_URL environment variable must be set")

    # ④: backend ingest config (session creation + event submission)
    model_id = int(os.getenv("MODEL_ID", "1"))

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
    notification_cooldown_sec = 120.0
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
        notification_cooldown_sec=notification_cooldown_sec,
        stream_id=stream_id,
        model_id=model_id,
        java_backend_url=java_backend_url,
        ai_service_token=ai_service_token,
        reconnect_wait_sec=reconnect_wait_sec,
        max_runtime_sec=max_runtime_sec,
        max_eval_windows=max_eval_windows,
        log_every_n_windows=log_every_n_windows,
    )


if __name__ == "__main__":
    main()
