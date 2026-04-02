#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Local demo for "stream-like" inference on a long video file.

Pipeline architecture:
- multi-process decoding/sampling (DataLoader workers)
- single-process GPU batched inference (main process)
- temporal persistence decision for alarm segments
"""
from __future__ import annotations

import math
from collections import deque
from dataclasses import dataclass
from pathlib import Path

import av
import numpy as np
import pandas as pd
import torch
from torch.utils.data import DataLoader, IterableDataset, get_worker_info
from tqdm import tqdm
from transformers import VideoMAEForVideoClassification, VideoMAEImageProcessor


_PROCESSOR_CACHE: dict[str, VideoMAEImageProcessor] = {}


def safe_log_text(value: object) -> str:
    text = str(value)
    try:
        text.encode("gbk")
        return text
    except UnicodeEncodeError:
        return text.encode("unicode_escape").decode("ascii")


def get_cached_processor(model_dir: Path) -> VideoMAEImageProcessor:
    key = str(model_dir.resolve())
    processor = _PROCESSOR_CACHE.get(key)
    if processor is None:
        processor = VideoMAEImageProcessor.from_pretrained(model_dir)
        _PROCESSOR_CACHE[key] = processor
    return processor


def sample_frame_indices(
        total_frames: int,
        num_frames: int = 16,
        sampling_rate: int = 4,
) -> list[int]:
    clip_len = num_frames * sampling_rate
    if total_frames <= 0:
        raise ValueError("Video contains no decodable frames.")

    if total_frames >= clip_len:
        start_idx = (total_frames - clip_len) // 2
        indices = start_idx + np.arange(num_frames) * sampling_rate
        indices = np.clip(indices, 0, total_frames - 1)
        return indices.astype(int).tolist()

    indices = np.linspace(0, total_frames - 1, num=num_frames)
    indices = np.clip(np.round(indices).astype(int), 0, total_frames - 1)
    return indices.tolist()


def resolve_fps(stream: av.video.stream.VideoStream) -> float:
    for value in (stream.average_rate, stream.base_rate):
        try:
            if value is not None:
                fps = float(value)
                if fps > 0:
                    return fps
        except Exception:
            continue
    return 25.0


def maybe_downscale_frame(
        frame: av.VideoFrame,
        max_short_side: int | None,
) -> av.VideoFrame:
    if not max_short_side or max_short_side <= 0:
        return frame

    h = int(frame.height)
    w = int(frame.width)
    short_side = min(h, w)
    if short_side <= max_short_side:
        return frame

    scale = max_short_side / float(short_side)
    new_w = max(1, int(round(w * scale)))
    new_h = max(1, int(round(h * scale)))
    return frame.reformat(width=new_w, height=new_h)


def frame_time_sec(frame: av.VideoFrame) -> float | None:
    try:
        if frame.pts is not None and frame.time_base is not None:
            return float(frame.pts * frame.time_base)
    except Exception:
        pass
    try:
        if frame.time is not None:
            return float(frame.time)
    except Exception:
        pass
    return None


def safe_seek_to_sec(
        container: av.container.InputContainer,
        stream: av.video.stream.VideoStream,
        sec: float,
) -> None:
    if sec <= 0:
        return
    if stream.time_base is None:
        return
    try:
        target = int(sec / float(stream.time_base))
        container.seek(max(0, target), stream=stream, any_frame=False, backward=True)
    except Exception:
        # If seek fails, decode from current position (or file start).
        pass


def estimate_total_frames(
        video_path: Path,
        decode_thread_type: str | None,
) -> tuple[float, int]:
    container = av.open(str(video_path))
    try:
        if not container.streams.video:
            raise ValueError(f"No video stream found: {video_path}")
        stream = container.streams.video[0]
        if decode_thread_type:
            stream.thread_type = decode_thread_type

        fps = resolve_fps(stream)

        if stream.frames and stream.frames > 0:
            return fps, int(stream.frames)

        if stream.duration is not None and stream.time_base is not None:
            duration_sec = float(stream.duration * stream.time_base)
            est_frames = int(round(duration_sec * fps))
            if est_frames > 0:
                return fps, est_frames

        # Fallback: explicit counting (slow, but robust).
        count = 0
        for _ in container.decode(video=0):
            count += 1
        return fps, max(1, count)
    finally:
        container.close()


def label_for_id(model: VideoMAEForVideoClassification, label_id: int) -> str:
    id2label = model.config.id2label
    if str(label_id) in id2label:
        return str(id2label[str(label_id)])
    return str(id2label[label_id])


def label_to_id(model: VideoMAEForVideoClassification, label: str) -> int:
    label2id = model.config.label2id
    if label in label2id:
        return int(label2id[label])

    lowered = label.lower()
    for key, value in label2id.items():
        if str(key).lower() == lowered:
            return int(value)

    supported = [label_for_id(model, i) for i in range(int(model.config.num_labels))]
    raise ValueError(
        f"target_label '{label}' not found in model labels. Supported labels: {supported}"
    )


@dataclass
class AlarmSegment:
    start_sec: float
    end_sec: float

    @property
    def duration_sec(self) -> float:
        return max(0.0, self.end_sec - self.start_sec)


class StreamingWindowDecodeDataset(IterableDataset):
    def __init__(
            self,
            video_path: Path,
            model_dir: Path,
            fps: float,
            total_frames: int,
            window_sec: float,
            step_sec: float,
            num_frames: int,
            sampling_rate: int,
            max_short_side: int | None,
            decode_thread_type: str | None,
            max_eval_windows: int | None = None,
    ) -> None:
        self.video_path = video_path
        self.model_dir = model_dir
        self.fps = float(fps)
        self.total_frames = int(total_frames)
        self.window_sec = float(window_sec)
        self.step_sec = float(step_sec)
        self.num_frames = int(num_frames)
        self.sampling_rate = int(sampling_rate)
        self.max_short_side = max_short_side
        self.decode_thread_type = decode_thread_type

        min_window_frames = max(1, int(round(self.window_sec * self.fps)))
        required_clip_len = max(1, self.num_frames * self.sampling_rate)
        self.window_frames = max(min_window_frames, required_clip_len)
        self.step_frames = max(1, int(round(self.step_sec * self.fps)))

        eval_frame_indices = list(range(self.window_frames - 1, self.total_frames, self.step_frames))
        if max_eval_windows is not None:
            eval_frame_indices = eval_frame_indices[:max(0, int(max_eval_windows))]
        self.eval_frame_indices = eval_frame_indices

    def _local_eval_indices(self) -> list[int]:
        if not self.eval_frame_indices:
            return []

        worker_info = get_worker_info()
        if worker_info is None:
            return self.eval_frame_indices

        per_worker = int(math.ceil(len(self.eval_frame_indices) / worker_info.num_workers))
        start = worker_info.id * per_worker
        end = min(len(self.eval_frame_indices), start + per_worker)
        return self.eval_frame_indices[start:end]

    def __iter__(self):
        local_eval_indices = self._local_eval_indices()
        if not local_eval_indices:
            return

        processor = get_cached_processor(self.model_dir)

        local_start_frame = max(0, local_eval_indices[0] - self.window_frames + 1)
        local_end_frame = local_eval_indices[-1]

        container = av.open(str(self.video_path))
        try:
            if not container.streams.video:
                for eval_frame_idx in local_eval_indices:
                    yield {
                        "eval_frame_idx": int(eval_frame_idx),
                        "ts_sec": float(eval_frame_idx / self.fps),
                        "pixel_values": None,
                        "error": "No video stream",
                    }
                return

            stream = container.streams.video[0]
            if self.decode_thread_type:
                stream.thread_type = self.decode_thread_type

            # Slightly seek earlier than local_start to avoid keyframe boundary misses.
            seek_start_sec = max(0.0, (local_start_frame / self.fps) - 2.0)
            safe_seek_to_sec(container, stream, seek_start_sec)

            frame_buffer: deque[np.ndarray] = deque(maxlen=self.window_frames)
            next_eval_ptr = 0
            prev_abs_frame_idx = -1

            for frame in container.decode(video=0):
                ts_sec = frame_time_sec(frame)
                if ts_sec is None:
                    abs_frame_idx = prev_abs_frame_idx + 1
                else:
                    estimated = int(round(ts_sec * self.fps))
                    abs_frame_idx = estimated if estimated > prev_abs_frame_idx else (prev_abs_frame_idx + 1)
                prev_abs_frame_idx = abs_frame_idx

                if abs_frame_idx < local_start_frame:
                    continue

                if abs_frame_idx > local_end_frame and next_eval_ptr >= len(local_eval_indices):
                    break

                frame = maybe_downscale_frame(frame, max_short_side=self.max_short_side)
                frame_buffer.append(frame.to_ndarray(format="rgb24"))

                while next_eval_ptr < len(local_eval_indices) and abs_frame_idx >= local_eval_indices[next_eval_ptr]:
                    eval_frame_idx = local_eval_indices[next_eval_ptr]

                    if len(frame_buffer) < self.window_frames:
                        yield {
                            "eval_frame_idx": int(eval_frame_idx),
                            "ts_sec": float(eval_frame_idx / self.fps),
                            "pixel_values": None,
                            "error": "Insufficient frames in local window buffer",
                        }
                    else:
                        try:
                            idx = sample_frame_indices(
                                total_frames=len(frame_buffer),
                                num_frames=self.num_frames,
                                sampling_rate=self.sampling_rate,
                            )
                            window_list = list(frame_buffer)
                            sampled_frames = [window_list[i] for i in idx]
                            pixel_values = processor(sampled_frames, return_tensors="pt")["pixel_values"].squeeze(0)

                            yield {
                                "eval_frame_idx": int(eval_frame_idx),
                                "ts_sec": float(eval_frame_idx / self.fps),
                                "pixel_values": pixel_values.contiguous(),
                                "error": None,
                            }
                        except Exception as e:
                            yield {
                                "eval_frame_idx": int(eval_frame_idx),
                                "ts_sec": float(eval_frame_idx / self.fps),
                                "pixel_values": None,
                                "error": f"{type(e).__name__}: {e}",
                            }

                    next_eval_ptr += 1

                if next_eval_ptr >= len(local_eval_indices) and abs_frame_idx >= local_end_frame:
                    break

            for i in range(next_eval_ptr, len(local_eval_indices)):
                eval_frame_idx = local_eval_indices[i]
                yield {
                    "eval_frame_idx": int(eval_frame_idx),
                    "ts_sec": float(eval_frame_idx / self.fps),
                    "pixel_values": None,
                    "error": "Reached decode end before this window was produced",
                }
        finally:
            container.close()


def stream_collate_fn(batch: list[dict]) -> dict:
    valid = [item for item in batch if item["pixel_values"] is not None]
    failed = [item for item in batch if item["pixel_values"] is None]

    if not valid:
        return {
            "pixel_values": None,
            "eval_frame_idx": [],
            "ts_sec": [],
            "failed_items": failed,
        }

    valid.sort(key=lambda x: int(x["eval_frame_idx"]))
    pixel_values = torch.stack([item["pixel_values"] for item in valid], dim=0)

    return {
        "pixel_values": pixel_values,
        "eval_frame_idx": [int(item["eval_frame_idx"]) for item in valid],
        "ts_sec": [float(item["ts_sec"]) for item in valid],
        "failed_items": failed,
    }


def apply_persistence(
        prediction_df: pd.DataFrame,
        target_label: str,
        clip_positive_threshold: float,
        persistence_window_sec: float,
        persistence_hit_ratio: float,
        clear_hit_ratio: float,
        min_history_sec: float,
        min_hits: int,
) -> tuple[pd.DataFrame, list[AlarmSegment]]:
    history: deque[tuple[float, int]] = deque()
    timeline_rows: list[dict] = []
    segments: list[AlarmSegment] = []

    alarm_on = False
    alarm_start_sec: float | None = None

    for row in prediction_df.itertuples(index=False):
        ts_sec = float(row.ts_sec)
        target_prob = float(row.target_prob)
        is_hit = int(target_prob >= clip_positive_threshold)

        history.append((ts_sec, is_hit))
        history_start_limit = ts_sec - persistence_window_sec
        while history and history[0][0] < history_start_limit:
            history.popleft()

        history_count = len(history)
        hit_count = int(sum(h for _, h in history))
        hit_ratio = float(hit_count / history_count) if history_count > 0 else 0.0
        history_span_sec = history[-1][0] - history[0][0] if history_count > 1 else 0.0
        history_ready = history_span_sec >= min_history_sec

        should_turn_on = history_ready and hit_count >= min_hits and hit_ratio >= persistence_hit_ratio
        should_turn_off = history_ready and hit_ratio <= clear_hit_ratio

        if not alarm_on and should_turn_on:
            alarm_on = True
            alarm_start_sec = ts_sec
        elif alarm_on and should_turn_off:
            segments.append(
                AlarmSegment(
                    start_sec=float(alarm_start_sec if alarm_start_sec is not None else ts_sec),
                    end_sec=ts_sec,
                )
            )
            alarm_on = False
            alarm_start_sec = None

        timeline_rows.append({
            "eval_index": int(row.eval_index),
            "eval_frame_idx": int(row.eval_frame_idx),
            "ts_sec": ts_sec,
            "pred_label": str(row.pred_label),
            "pred_conf": float(row.pred_conf),
            "target_label": target_label,
            "target_prob": target_prob,
            "clip_hit": is_hit,
            "rolling_count": history_count,
            "rolling_hit_count": hit_count,
            "rolling_hit_ratio": hit_ratio,
            "alarm_on": int(alarm_on),
        })

    if alarm_on and len(timeline_rows) > 0:
        segments.append(
            AlarmSegment(
                start_sec=float(alarm_start_sec if alarm_start_sec is not None else timeline_rows[-1]["ts_sec"]),
                end_sec=float(timeline_rows[-1]["ts_sec"]),
            )
        )

    timeline_df = pd.DataFrame(timeline_rows)
    return timeline_df, segments


def run_demo(
        video_path: Path,
        model_dir: Path,
        output_dir: Path,
        target_label: str = "wander",
        window_sec: float = 5.0,
        step_sec: float = 2.0,
        num_frames: int = 16,
        sampling_rate: int = 4,
        max_short_side: int | None = 360,
        decode_thread_type: str | None = "AUTO",
        clip_positive_threshold: float = 0.60,
        persistence_window_sec: float = 120.0,
        persistence_hit_ratio: float = 0.60,
        clear_hit_ratio: float = 0.40,
        min_history_sec: float = 110.0,
        min_hits: int = 34,
        decode_workers: int = 4,
        infer_batch_size: int = 8,
        infer_prefetch_factor: int = 2,
        max_eval_windows: int | None = None,
) -> None:
    if not video_path.exists():
        raise FileNotFoundError(f"video_path not found: {video_path}")
    if not model_dir.exists():
        raise FileNotFoundError(f"model_dir not found: {model_dir}")

    device = "cuda" if torch.cuda.is_available() else "cpu"
    use_cuda = device == "cuda"
    if use_cuda:
        torch.backends.cudnn.benchmark = True

    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    model.to(device)
    model.eval()
    target_id = label_to_id(model, target_label)

    fps, total_frames = estimate_total_frames(video_path, decode_thread_type=decode_thread_type)

    dataset = StreamingWindowDecodeDataset(
        video_path=video_path,
        model_dir=model_dir,
        fps=fps,
        total_frames=total_frames,
        window_sec=window_sec,
        step_sec=step_sec,
        num_frames=num_frames,
        sampling_rate=sampling_rate,
        max_short_side=max_short_side,
        decode_thread_type=decode_thread_type,
        max_eval_windows=max_eval_windows,
    )

    total_expected = len(dataset.eval_frame_indices)
    if total_expected == 0:
        raise ValueError("No evaluation windows generated. Check video duration and window settings.")

    decode_workers = max(0, int(decode_workers))
    infer_batch_size = max(1, int(infer_batch_size))

    def create_dataloader(active_workers: int) -> DataLoader:
        kwargs = {
            "dataset": dataset,
            "batch_size": infer_batch_size,
            "num_workers": active_workers,
            "pin_memory": use_cuda,
            "persistent_workers": (active_workers > 0),
            "collate_fn": stream_collate_fn,
            "shuffle": False,
        }
        if active_workers > 0:
            kwargs["prefetch_factor"] = max(1, int(infer_prefetch_factor))
        return DataLoader(**kwargs)

    active_decode_workers = decode_workers
    dataloader = create_dataloader(active_decode_workers)
    progress_total = int(math.ceil(total_expected / infer_batch_size))

    failed_count = 0
    failed_log_limit = 10
    prediction_rows: list[dict] = []
    eval_index_counter = 0

    while True:
        try:
            with torch.inference_mode():
                for batch in tqdm(dataloader, total=progress_total, desc="Decode + GPU batched inference"):
                    failed_items = batch["failed_items"]
                    failed_count += len(failed_items)
                    if failed_items and failed_log_limit > 0:
                        for item in failed_items[:failed_log_limit]:
                            print(
                                f"[WARN] failed window idx={item['eval_frame_idx']} "
                                f"ts={item['ts_sec']:.3f}s ({safe_log_text(item['error'])})"
                            )
                        failed_log_limit -= min(failed_log_limit, len(failed_items))

                    if batch["pixel_values"] is None:
                        continue

                    pixel_values = batch["pixel_values"].to(device, non_blocking=use_cuda)
                    outputs = model(pixel_values=pixel_values)
                    probs = torch.softmax(outputs.logits, dim=-1).detach().cpu().numpy()
                    pred_ids = np.argmax(probs, axis=-1).tolist()

                    for i, eval_frame_idx in enumerate(batch["eval_frame_idx"]):
                        eval_index_counter += 1
                        pred_id = int(pred_ids[i])
                        pred_label = label_for_id(model, pred_id)
                        pred_conf = float(probs[i][pred_id])
                        target_prob = float(probs[i][target_id])

                        prediction_rows.append({
                            "eval_index": eval_index_counter,
                            "eval_frame_idx": int(eval_frame_idx),
                            "ts_sec": float(batch["ts_sec"][i]),
                            "pred_label": pred_label,
                            "pred_conf": pred_conf,
                            "target_prob": target_prob,
                        })
            break
        except PermissionError as e:
            if active_decode_workers > 0:
                print(
                    "[WARN] multiprocessing decode init failed, "
                    f"fallback to single-process decode. detail={safe_log_text(e)}"
                )
                active_decode_workers = 0
                dataloader = create_dataloader(active_decode_workers)
                failed_count = 0
                failed_log_limit = 10
                prediction_rows = []
                eval_index_counter = 0
                continue
            raise

    if not prediction_rows:
        raise RuntimeError("No valid windows were inferred. Please inspect warning logs.")

    pred_df = pd.DataFrame(prediction_rows).sort_values("eval_frame_idx").reset_index(drop=True)
    pred_df["eval_index"] = np.arange(1, len(pred_df) + 1)

    timeline_df, segments = apply_persistence(
        prediction_df=pred_df,
        target_label=target_label,
        clip_positive_threshold=clip_positive_threshold,
        persistence_window_sec=persistence_window_sec,
        persistence_hit_ratio=persistence_hit_ratio,
        clear_hit_ratio=clear_hit_ratio,
        min_history_sec=min_history_sec,
        min_hits=min_hits,
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    timeline_path = output_dir / f"{video_path.stem}_timeline.csv"
    alarms_path = output_dir / f"{video_path.stem}_alarms.csv"

    timeline_df.to_csv(timeline_path, index=False, encoding="utf-8")

    alarm_rows = [
        {
            "alarm_index": i + 1,
            "start_sec": segment.start_sec,
            "end_sec": segment.end_sec,
            "duration_sec": segment.duration_sec,
        }
        for i, segment in enumerate(segments)
    ]
    pd.DataFrame(alarm_rows).to_csv(alarms_path, index=False, encoding="utf-8")

    clip_hit_rate = float(timeline_df["clip_hit"].mean()) if len(timeline_df) > 0 else 0.0
    avg_target_prob = float(timeline_df["target_prob"].mean()) if len(timeline_df) > 0 else 0.0
    max_target_prob = float(timeline_df["target_prob"].max()) if len(timeline_df) > 0 else 0.0

    print("\n===== Streaming Demo Summary =====")
    print(f"video_path: {safe_log_text(video_path)}")
    print(f"model_dir: {safe_log_text(model_dir)}")
    print(f"device: {device}")
    print(f"target_label: {target_label}")
    print(f"fps: {fps:.4f}")
    print(f"estimated_total_frames: {total_frames}")
    print(f"expected_windows: {total_expected}")
    print(f"evaluated_windows: {len(timeline_df)}")
    print(f"failed_windows: {failed_count}")
    print(f"decode_workers: {active_decode_workers} (requested: {decode_workers})")
    print(f"infer_batch_size: {infer_batch_size}")
    print(f"infer_prefetch_factor: {infer_prefetch_factor if decode_workers > 0 else 'N/A'}")
    print(f"decode_thread_type: {decode_thread_type}")
    print(f"max_short_side: {max_short_side}")
    print(f"clip_positive_threshold: {clip_positive_threshold}")
    print(f"persistence_window_sec: {persistence_window_sec}")
    print(f"persistence_hit_ratio: {persistence_hit_ratio}")
    print(f"clear_hit_ratio: {clear_hit_ratio}")
    print(f"min_history_sec: {min_history_sec}")
    print(f"min_hits: {min_hits}")
    print(f"clip_hit_rate: {clip_hit_rate:.6f}")
    print(f"avg_target_prob: {avg_target_prob:.6f}")
    print(f"max_target_prob: {max_target_prob:.6f}")
    print(f"alarm_segments: {len(segments)}")
    print(f"timeline_csv: {safe_log_text(timeline_path)}")
    print(f"alarms_csv: {safe_log_text(alarms_path)}")

    if segments:
        print("\nTop alarm segments:")
        for i, segment in enumerate(segments[:10], start=1):
            print(
                f"  #{i} start={segment.start_sec:.2f}s, "
                f"end={segment.end_sec:.2f}s, duration={segment.duration_sec:.2f}s"
            )


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parent.parent

    # Input / Output
    video_path = Path(r"D:\ai-kids-care\ai\data\raw\이상행동 CCTV 영상\06.배회(wander)\inside_croki_01\144-5\144-5_cam01_wander03_place01_night_spring.mp4")
    model_dir = project_root / "outputs" / "06_wander_videomae_baseline" / "best_model"
    output_dir = project_root / "outputs" / "predictions" / "streaming_demo"

    # Sliding inference setup
    target_label = "wander"
    window_sec = 5.0
    step_sec = 2.0
    num_frames = 16
    sampling_rate = 4
    max_short_side = 360
    decode_thread_type = "AUTO"

    # Persistence decision setup (Balanced)
    clip_positive_threshold = 0.50
    persistence_window_sec = 120.0
    persistence_hit_ratio = 0.4
    clear_hit_ratio = 0.40
    min_history_sec = 110.0
    min_hits = 30

    # Throughput setup
    decode_workers = 6
    infer_batch_size = 12
    infer_prefetch_factor = 2
    max_eval_windows = None  # set e.g. 80 for quick profiling

    if not video_path.exists():
        raise FileNotFoundError(
            f"Please set an existing long video path in __main__: {video_path}"
        )

    run_demo(
        video_path=video_path.resolve(),
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
        decode_workers=decode_workers,
        infer_batch_size=infer_batch_size,
        infer_prefetch_factor=infer_prefetch_factor,
        max_eval_windows=max_eval_windows,
    )
