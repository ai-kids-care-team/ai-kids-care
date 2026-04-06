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
            decode_backend: str = "pyav_cpu",
            ffmpeg_path: str = "ffmpeg",
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
        self.decode_backend = str(decode_backend).strip().lower()
        self.ffmpeg_path = str(ffmpeg_path)

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

    @staticmethod
    def _resolve_scaled_size(width: int, height: int, max_short_side: int | None) -> tuple[int, int]:
        if not max_short_side or max_short_side <= 0:
            return int(width), int(height)

        w = int(width)
        h = int(height)
        short_side = min(h, w)
        if short_side <= max_short_side:
            return w, h

        scale = max_short_side / float(short_side)
        new_w = max(1, int(round(w * scale)))
        new_h = max(1, int(round(h * scale)))
        return new_w, new_h

    @staticmethod
    def _cuvid_decoder_name(codec_name: str) -> str | None:
        mapping = {
            "h264": "h264_cuvid",
            "hevc": "hevc_cuvid",
            "mpeg4": "mpeg4_cuvid",
            "mpeg2video": "mpeg2_cuvid",
            "mpeg1video": "mpeg1_cuvid",
            "vc1": "vc1_cuvid",
            "vp8": "vp8_cuvid",
            "vp9": "vp9_cuvid",
            "mjpeg": "mjpeg_cuvid",
            "av1": "av1_cuvid",
        }
        return mapping.get(str(codec_name).lower())

    def _decode_with_pyav(
            self,
            local_start_frame: int,
            local_end_frame: int,
    ):
        container = av.open(str(self.video_path))
        try:
            if not container.streams.video:
                raise ValueError("No video stream")

            stream = container.streams.video[0]
            if self.decode_thread_type:
                stream.thread_type = self.decode_thread_type

            seek_start_sec = max(0.0, (local_start_frame / self.fps) - 2.0)
            safe_seek_to_sec(container, stream, seek_start_sec)

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
                if abs_frame_idx > local_end_frame:
                    break

                frame = maybe_downscale_frame(frame, max_short_side=self.max_short_side)
                yield abs_frame_idx, frame.to_ndarray(format="rgb24")
        finally:
            container.close()

    def _decode_with_ffmpeg_gpu(
            self,
            local_start_frame: int,
            local_end_frame: int,
    ):
        import subprocess

        probe = av.open(str(self.video_path))
        try:
            if not probe.streams.video:
                raise ValueError("No video stream")
            stream = probe.streams.video[0]
            codec_name = str(stream.codec_context.name)
            in_w = int(stream.codec_context.width)
            in_h = int(stream.codec_context.height)
        finally:
            probe.close()

        decoder_name = self._cuvid_decoder_name(codec_name)
        if decoder_name is None:
            raise ValueError(f"Unsupported GPU decoder mapping for codec='{codec_name}'")

        out_w, out_h = self._resolve_scaled_size(in_w, in_h, self.max_short_side)

        seek_start_sec = max(0.0, (local_start_frame / self.fps) - 2.0)
        seek_start_frame = max(0, int(round(seek_start_sec * self.fps)))
        frame_cap = max(1, local_end_frame - seek_start_frame + 1)

        vf_expr = f"scale_cuda=w={out_w}:h={out_h},hwdownload,format=nv12,format=rgb24"

        cmd = [
            self.ffmpeg_path,
            "-hide_banner",
            "-loglevel",
            "error",
            "-hwaccel",
            "cuda",
            "-hwaccel_output_format",
            "cuda",
            "-c:v",
            decoder_name,
            "-ss",
            f"{seek_start_sec:.6f}",
            "-i",
            str(self.video_path),
            "-an",
            "-sn",
            "-dn",
            "-vsync",
            "0",
            "-vf",
            vf_expr,
            "-frames:v",
            str(frame_cap),
            "-f",
            "rawvideo",
            "-pix_fmt",
            "rgb24",
            "pipe:1",
        ]

        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            bufsize=10 ** 8,
        )

        if proc.stdout is None:
            proc.kill()
            raise RuntimeError("Failed to open ffmpeg stdout pipe")

        frame_size = out_w * out_h * 3
        frame_idx = 0
        try:
            while True:
                raw = proc.stdout.read(frame_size)
                if len(raw) < frame_size:
                    break

                abs_frame_idx = seek_start_frame + frame_idx
                frame_idx += 1

                if abs_frame_idx < local_start_frame:
                    continue
                if abs_frame_idx > local_end_frame:
                    break

                arr = np.frombuffer(raw, dtype=np.uint8).reshape((out_h, out_w, 3)).copy()
                yield abs_frame_idx, arr
        finally:
            try:
                proc.stdout.close()
            except Exception:
                pass
            proc.wait(timeout=10)

        if proc.returncode not in (0, None):
            raise RuntimeError(f"ffmpeg gpu decode failed, returncode={proc.returncode}")

    def __iter__(self):
        local_eval_indices = self._local_eval_indices()
        if not local_eval_indices:
            return

        processor = get_cached_processor(self.model_dir)

        local_start_frame = max(0, local_eval_indices[0] - self.window_frames + 1)
        local_end_frame = local_eval_indices[-1]

        frame_buffer: deque[np.ndarray] = deque(maxlen=self.window_frames)
        next_eval_ptr = 0

        try:
            backend = self.decode_backend
            if backend == "ffmpeg_gpu":
                def frame_iter_with_fallback():
                    decoded_any = False
                    try:
                        for item in self._decode_with_ffmpeg_gpu(local_start_frame, local_end_frame):
                            decoded_any = True
                            yield item
                    except Exception as e:
                        if decoded_any:
                            raise
                        print(
                            "[WARN] ffmpeg_gpu decode failed before first frame, fallback to pyav_cpu. "
                            f"detail={safe_log_text(type(e).__name__ + ': ' + str(e))}"
                        )
                        for item in self._decode_with_pyav(local_start_frame, local_end_frame):
                            yield item

                frame_iter = frame_iter_with_fallback()
            else:
                frame_iter = self._decode_with_pyav(local_start_frame, local_end_frame)

            for abs_frame_idx, rgb_frame in frame_iter:
                if abs_frame_idx < local_start_frame:
                    continue
                if abs_frame_idx > local_end_frame and next_eval_ptr >= len(local_eval_indices):
                    break

                frame_buffer.append(rgb_frame)

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
        except Exception as e:
            err = f"{type(e).__name__}: {e}"
            for i in range(next_eval_ptr, len(local_eval_indices)):
                eval_frame_idx = local_eval_indices[i]
                yield {
                    "eval_frame_idx": int(eval_frame_idx),
                    "ts_sec": float(eval_frame_idx / self.fps),
                    "pixel_values": None,
                    "error": err,
                }
            return

        for i in range(next_eval_ptr, len(local_eval_indices)):
            eval_frame_idx = local_eval_indices[i]
            yield {
                "eval_frame_idx": int(eval_frame_idx),
                "ts_sec": float(eval_frame_idx / self.fps),
                "pixel_values": None,
                "error": "Reached decode end before this window was produced",
            }


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


def collect_video_files_from_manifest(
        manifest_path: Path,
        split: str = "test",
        source_video_col: str = "source_video",
) -> list[Path]:
    if not manifest_path.exists():
        raise FileNotFoundError(f"manifest_path not found: {manifest_path}")

    df = pd.read_csv(manifest_path)
    if source_video_col not in df.columns:
        raise ValueError(f"Column '{source_video_col}' not found in manifest: {manifest_path}")
    if "split" not in df.columns:
        raise ValueError(f"Column 'split' not found in manifest: {manifest_path}")

    filtered = df[df["split"].astype(str) == str(split)].copy()
    if len(filtered) == 0:
        raise ValueError(f"No rows found for split='{split}' in manifest: {manifest_path}")

    files: list[Path] = []
    seen: set[str] = set()
    for raw_path in filtered[source_video_col].astype(str).tolist():
        path = Path(raw_path)
        key = str(path.resolve()) if path.exists() else str(path)
        if key in seen:
            continue
        seen.add(key)
        files.append(path)

    return files


def infer_window_predictions(
        video_path: Path,
        model: VideoMAEForVideoClassification,
        model_dir: Path,
        target_id: int,
        device: str,
        window_sec: float,
        step_sec: float,
        num_frames: int,
        sampling_rate: int,
        max_short_side: int | None,
        decode_thread_type: str | None,
        decode_workers: int,
        infer_batch_size: int,
        infer_prefetch_factor: int,
        max_eval_windows: int | None,
        decode_backend: str = "pyav_cpu",
        ffmpeg_path: str = "ffmpeg",
) -> tuple[pd.DataFrame, dict]:
    use_cuda = device == "cuda"

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
        decode_backend=decode_backend,
        ffmpeg_path=ffmpeg_path,
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

    infer_stats = {
        "fps": float(fps),
        "total_frames": int(total_frames),
        "total_expected": int(total_expected),
        "failed_count": int(failed_count),
        "active_decode_workers": int(active_decode_workers),
        "decode_workers_requested": int(decode_workers),
        "infer_batch_size": int(infer_batch_size),
        "infer_prefetch_factor": int(infer_prefetch_factor),
        "decode_backend": str(decode_backend),
    }
    return pred_df, infer_stats


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
        decode_backend: str = "ffmpeg_gpu",
        ffmpeg_path: str = "ffmpeg",
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
) -> dict:
    if not video_path.exists():
        raise FileNotFoundError(f"video_path not found: {video_path}")
    if not model_dir.exists():
        raise FileNotFoundError(f"model_dir not found: {model_dir}")

    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cuda":
        torch.backends.cudnn.benchmark = True

    model = VideoMAEForVideoClassification.from_pretrained(model_dir)
    model.to(device)
    model.eval()
    target_id = label_to_id(model, target_label)

    pred_df, infer_stats = infer_window_predictions(
        video_path=video_path,
        model=model,
        model_dir=model_dir,
        target_id=target_id,
        device=device,
        window_sec=window_sec,
        step_sec=step_sec,
        num_frames=num_frames,
        sampling_rate=sampling_rate,
        max_short_side=max_short_side,
        decode_thread_type=decode_thread_type,
        decode_backend=decode_backend,
        ffmpeg_path=ffmpeg_path,
        decode_workers=decode_workers,
        infer_batch_size=infer_batch_size,
        infer_prefetch_factor=infer_prefetch_factor,
        max_eval_windows=max_eval_windows,
    )

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
    print(f"fps: {infer_stats['fps']:.4f}")
    print(f"estimated_total_frames: {infer_stats['total_frames']}")
    print(f"expected_windows: {infer_stats['total_expected']}")
    print(f"evaluated_windows: {len(timeline_df)}")
    print(f"failed_windows: {infer_stats['failed_count']}")
    print(
        f"decode_workers: {infer_stats['active_decode_workers']} "
        f"(requested: {infer_stats['decode_workers_requested']})"
    )
    print(f"infer_batch_size: {infer_stats['infer_batch_size']}")
    if infer_stats["decode_workers_requested"] > 0:
        print(f"infer_prefetch_factor: {infer_stats['infer_prefetch_factor']}")
    else:
        print("infer_prefetch_factor: N/A")
    print(f"decode_thread_type: {decode_thread_type}")
    print(f"decode_backend: {infer_stats['decode_backend']}")
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

    summary = {
        "video_path": str(video_path),
        "model_dir": str(model_dir),
        "target_label": target_label,
        "evaluated_windows": int(len(timeline_df)),
        "failed_windows": int(infer_stats["failed_count"]),
        "clip_hit_rate": float(clip_hit_rate),
        "avg_target_prob": float(avg_target_prob),
        "max_target_prob": float(max_target_prob),
        "alarm_segments": int(len(segments)),
        "alarm_total_duration_sec": float(sum(s.duration_sec for s in segments)),
        "first_alarm_sec": float(segments[0].start_sec) if segments else np.nan,
        "clip_positive_threshold": float(clip_positive_threshold),
        "persistence_hit_ratio": float(persistence_hit_ratio),
        "clear_hit_ratio": float(clear_hit_ratio),
        "min_history_sec": float(min_history_sec),
        "min_hits": int(min_hits),
        "decode_workers_actual": int(infer_stats["active_decode_workers"]),
    }
    return summary


def run_batch_positive_threshold_sweep(
        manifest_path: Path,
        manifest_split: str,
        model_dir: Path,
        output_dir: Path,
        target_label: str,
        window_sec: float,
        step_sec: float,
        num_frames: int,
        sampling_rate: int,
        max_short_side: int | None,
        decode_thread_type: str | None,
        persistence_window_sec: float,
        clear_hit_ratio: float,
        min_history_sec: float,
        min_hits: int,
        decode_workers: int,
        infer_batch_size: int,
        infer_prefetch_factor: int,
        max_eval_windows: int | None,
        clip_positive_threshold_candidates: list[float],
        persistence_hit_ratio_candidates: list[float],
        target_detection_rate_candidates: list[float],
        decode_backend: str = "ffmpeg_gpu",
        ffmpeg_path: str = "ffmpeg",
        reuse_window_prediction_cache: bool = True,
        save_window_prediction_cache: bool = True,
) -> None:
    import json

    if not model_dir.exists():
        raise FileNotFoundError(f"model_dir not found: {model_dir}")

    video_files = collect_video_files_from_manifest(
        manifest_path=manifest_path,
        split=manifest_split,
        source_video_col="source_video",
    )
    if not video_files:
        raise FileNotFoundError(
            f"No video files found from manifest split='{manifest_split}': {manifest_path}"
        )

    output_dir.mkdir(parents=True, exist_ok=True)

    infer_path = output_dir / "batch_positive_inference_stats.csv"
    window_cache_path = output_dir / "batch_positive_window_predictions.csv"
    window_cache_meta_path = output_dir / "batch_positive_window_predictions_meta.json"

    current_cache_meta = {
        "manifest_path": str(Path(manifest_path).resolve()),
        "manifest_split": str(manifest_split),
        "model_dir": str(Path(model_dir).resolve()),
        "target_label": str(target_label),
        "window_sec": float(window_sec),
        "step_sec": float(step_sec),
        "num_frames": int(num_frames),
        "sampling_rate": int(sampling_rate),
        "max_short_side": None if max_short_side is None else int(max_short_side),
        "decode_thread_type": None if decode_thread_type is None else str(decode_thread_type),
        "decode_backend": str(decode_backend),
        "max_eval_windows": None if max_eval_windows is None else int(max_eval_windows),
    }

    cached_pred_by_video: dict[str, pd.DataFrame] = {}
    cache_loaded = False
    if reuse_window_prediction_cache and window_cache_path.exists() and window_cache_meta_path.exists():
        try:
            cached_meta = json.loads(window_cache_meta_path.read_text(encoding="utf-8"))
            if cached_meta == current_cache_meta:
                cache_df = pd.read_csv(window_cache_path)
                required_cols = {
                    "video_path",
                    "eval_index",
                    "eval_frame_idx",
                    "ts_sec",
                    "pred_label",
                    "pred_conf",
                    "target_prob",
                }
                missing_cols = required_cols - set(cache_df.columns)
                if missing_cols:
                    print(
                        f"[WARN] Ignore cache due to missing columns: {sorted(missing_cols)} "
                        f"({safe_log_text(window_cache_path)})"
                    )
                else:
                    cache_df = cache_df[[
                        "video_path",
                        "eval_index",
                        "eval_frame_idx",
                        "ts_sec",
                        "pred_label",
                        "pred_conf",
                        "target_prob",
                    ]]
                    for video_key, part in cache_df.groupby("video_path", sort=False):
                        part_df = part[[
                            "eval_index",
                            "eval_frame_idx",
                            "ts_sec",
                            "pred_label",
                            "pred_conf",
                            "target_prob",
                        ]].copy()
                        part_df = part_df.sort_values("eval_frame_idx").reset_index(drop=True)
                        part_df["eval_index"] = np.arange(1, len(part_df) + 1)
                        cached_pred_by_video[str(video_key)] = part_df
                    cache_loaded = True
            else:
                print(
                    "[INFO] Skip cached window predictions because cache meta changed. "
                    f"cache={safe_log_text(window_cache_meta_path)}"
                )
        except Exception as e:
            print(
                "[WARN] Failed to read cached window predictions, will infer again. "
                f"detail={safe_log_text(type(e).__name__ + ': ' + str(e))}"
            )

    infer_stats_cached: dict[str, dict] = {}
    if infer_path.exists():
        try:
            old_infer_df = pd.read_csv(infer_path)
            if "video_path" in old_infer_df.columns:
                for row in old_infer_df.to_dict("records"):
                    infer_stats_cached[str(row.get("video_path"))] = row
        except Exception:
            infer_stats_cached = {}

    videos_to_infer = [vp for vp in video_files if str(vp) not in cached_pred_by_video]
    reused_video_count = len(video_files) - len(videos_to_infer)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cuda":
        torch.backends.cudnn.benchmark = True

    model = None
    target_id = None
    if len(videos_to_infer) > 0:
        model = VideoMAEForVideoClassification.from_pretrained(model_dir)
        model.to(device)
        model.eval()
        target_id = label_to_id(model, target_label)
    else:
        print("[INFO] All videos loaded from cached window predictions. No model inference needed.")

    combo_list: list[tuple[float, float]] = []
    for clip_th in clip_positive_threshold_candidates:
        for hit_ratio in persistence_hit_ratio_candidates:
            combo_list.append((float(clip_th), float(hit_ratio)))
    combo_list.sort(key=lambda x: (x[0], x[1]))

    per_video_rows: list[dict] = []
    infer_rows: list[dict] = []
    window_parts: list[pd.DataFrame] = []
    effective_step_sec = max(1e-6, float(step_sec))
    history_window_count = math.floor(float(min_history_sec) / effective_step_sec) + 1

    for video_path in tqdm(video_files, desc="Batch positive sweep", unit="video"):
        video_key = str(video_path)

        if video_key in cached_pred_by_video:
            pred_df = cached_pred_by_video[video_key].copy()
            cached_stat = infer_stats_cached.get(video_key, {})

            avg_target_prob = float(pred_df["target_prob"].mean()) if len(pred_df) > 0 else 0.0
            max_target_prob = float(pred_df["target_prob"].max()) if len(pred_df) > 0 else 0.0
            eval_windows = int(len(pred_df))

            infer_rows.append({
                "video_path": video_key,
                "evaluated_windows": eval_windows,
                "failed_windows": int(cached_stat.get("failed_windows", 0) or 0),
                "avg_target_prob": float(avg_target_prob),
                "max_target_prob": float(max_target_prob),
                "fps": float(cached_stat.get("fps", np.nan)),
                "estimated_total_frames": float(cached_stat.get("estimated_total_frames", np.nan)),
                "expected_windows": float(cached_stat.get("expected_windows", eval_windows)),
            })
            failed_windows = int(cached_stat.get("failed_windows", 0) or 0)
        else:
            if model is None or target_id is None:
                raise RuntimeError("Internal error: model was not initialized for uncached inference.")

            pred_df, infer_stats = infer_window_predictions(
                video_path=video_path,
                model=model,
                model_dir=model_dir,
                target_id=target_id,
                device=device,
                window_sec=window_sec,
                step_sec=step_sec,
                num_frames=num_frames,
                sampling_rate=sampling_rate,
                max_short_side=max_short_side,
                decode_thread_type=decode_thread_type,
                decode_backend=decode_backend,
                ffmpeg_path=ffmpeg_path,
                decode_workers=decode_workers,
                infer_batch_size=infer_batch_size,
                infer_prefetch_factor=infer_prefetch_factor,
                max_eval_windows=max_eval_windows,
            )

            avg_target_prob = float(pred_df["target_prob"].mean()) if len(pred_df) > 0 else 0.0
            max_target_prob = float(pred_df["target_prob"].max()) if len(pred_df) > 0 else 0.0

            infer_rows.append({
                "video_path": video_key,
                "evaluated_windows": int(len(pred_df)),
                "failed_windows": int(infer_stats["failed_count"]),
                "avg_target_prob": float(avg_target_prob),
                "max_target_prob": float(max_target_prob),
                "fps": float(infer_stats["fps"]),
                "estimated_total_frames": int(infer_stats["total_frames"]),
                "expected_windows": int(infer_stats["total_expected"]),
            })
            failed_windows = int(infer_stats["failed_count"])

        pred_df = pred_df.sort_values("eval_frame_idx").reset_index(drop=True)
        pred_df["eval_index"] = np.arange(1, len(pred_df) + 1)

        window_parts.append(
            pred_df.assign(video_path=video_key)[[
                "video_path",
                "eval_index",
                "eval_frame_idx",
                "ts_sec",
                "pred_label",
                "pred_conf",
                "target_prob",
            ]].copy()
        )

        for clip_th, hit_ratio in combo_list:
            combo_min_hits = max(1, int(math.ceil(history_window_count * float(hit_ratio))))
            timeline_df, segments = apply_persistence(
                prediction_df=pred_df,
                target_label=target_label,
                clip_positive_threshold=clip_th,
                persistence_window_sec=persistence_window_sec,
                persistence_hit_ratio=hit_ratio,
                clear_hit_ratio=clear_hit_ratio,
                min_history_sec=min_history_sec,
                min_hits=combo_min_hits,
            )

            detected = int(len(segments) > 0)
            first_alarm_sec = float(segments[0].start_sec) if segments else np.nan
            total_alarm_sec = float(sum(seg.duration_sec for seg in segments))
            max_alarm_sec = float(max((seg.duration_sec for seg in segments), default=0.0))
            clip_hit_rate = float(timeline_df["clip_hit"].mean()) if len(timeline_df) > 0 else 0.0

            per_video_rows.append({
                "video_path": video_key,
                "clip_positive_threshold": float(clip_th),
                "persistence_hit_ratio": float(hit_ratio),
                "min_hits_used": int(combo_min_hits),
                "detected": detected,
                "alarm_segments": int(len(segments)),
                "first_alarm_sec": first_alarm_sec,
                "alarm_total_duration_sec": total_alarm_sec,
                "alarm_max_duration_sec": max_alarm_sec,
                "clip_hit_rate": clip_hit_rate,
                "avg_target_prob": float(avg_target_prob),
                "max_target_prob": float(max_target_prob),
                "evaluated_windows": int(len(timeline_df)),
                "failed_windows": failed_windows,
            })

    infer_df = pd.DataFrame(infer_rows)
    per_video_df = pd.DataFrame(per_video_rows)

    summary_df = (
        per_video_df
        .groupby(["clip_positive_threshold", "persistence_hit_ratio"], as_index=False)
        .agg(
            min_hits_used=("min_hits_used", "first"),
            total_videos=("video_path", "count"),
            detected_videos=("detected", "sum"),
            detection_rate=("detected", "mean"),
            avg_first_alarm_sec=("first_alarm_sec", "mean"),
            median_first_alarm_sec=("first_alarm_sec", "median"),
            avg_alarm_total_duration_sec=("alarm_total_duration_sec", "mean"),
            avg_alarm_max_duration_sec=("alarm_max_duration_sec", "mean"),
            avg_clip_hit_rate=("clip_hit_rate", "mean"),
            avg_target_prob=("avg_target_prob", "mean"),
        )
    )

    summary_df = summary_df.sort_values(
        by=["detection_rate", "clip_positive_threshold", "persistence_hit_ratio"],
        ascending=[False, False, False],
    ).reset_index(drop=True)

    if len(target_detection_rate_candidates) == 0:
        raise ValueError("target_detection_rate_candidates must contain at least one value.")

    detection_targets = []
    seen_targets: set[float] = set()
    for one_target in target_detection_rate_candidates:
        target = float(one_target)
        if target in seen_targets:
            continue
        seen_targets.add(target)
        detection_targets.append(target)

    recommended_rows: list[pd.DataFrame] = []
    missed_rows: list[pd.DataFrame] = []

    for target in detection_targets:
        eligible_df = summary_df[summary_df["detection_rate"] >= float(target)]
        if len(eligible_df) > 0:
            one_recommended_df = eligible_df.sort_values(
                by=["clip_positive_threshold", "persistence_hit_ratio", "avg_first_alarm_sec"],
                ascending=[False, False, True],
            ).head(1).copy()
            recommendation_rule = (
                f"strictest threshold meeting detection_rate >= {float(target):.3f}"
            )
        else:
            one_recommended_df = summary_df.sort_values(
                by=["detection_rate", "avg_first_alarm_sec", "clip_positive_threshold", "persistence_hit_ratio"],
                ascending=[False, True, False, False],
            ).head(1).copy()
            recommendation_rule = "best detection_rate fallback (target_detection_rate not reached)"

        one_recommended_df.insert(0, "recommendation_rule", recommendation_rule)
        one_recommended_df.insert(0, "target_detection_rate", float(target))
        recommended_rows.append(one_recommended_df)

        best_clip_th = float(one_recommended_df.iloc[0]["clip_positive_threshold"])
        best_hit_ratio = float(one_recommended_df.iloc[0]["persistence_hit_ratio"])
        one_missed_df = per_video_df[
            (per_video_df["clip_positive_threshold"] == best_clip_th)
            & (per_video_df["persistence_hit_ratio"] == best_hit_ratio)
            & (per_video_df["detected"] == 0)
            ].copy()
        one_missed_df.insert(0, "recommendation_rule", recommendation_rule)
        one_missed_df.insert(0, "target_detection_rate", float(target))
        missed_rows.append(one_missed_df)

    recommended_df = pd.concat(recommended_rows, ignore_index=True)
    missed_df = pd.concat(missed_rows, ignore_index=True) if missed_rows else pd.DataFrame()

    per_video_path = output_dir / "batch_positive_per_video_per_threshold.csv"
    summary_path = output_dir / "batch_positive_threshold_summary.csv"
    recommended_path = output_dir / "batch_positive_recommended_threshold.csv"
    missed_path = output_dir / "batch_positive_missed_videos_at_recommended_threshold.csv"

    infer_df.to_csv(infer_path, index=False, encoding="utf-8")
    per_video_df.to_csv(per_video_path, index=False, encoding="utf-8")
    summary_df.to_csv(summary_path, index=False, encoding="utf-8")
    recommended_df.to_csv(recommended_path, index=False, encoding="utf-8")
    missed_df.to_csv(missed_path, index=False, encoding="utf-8")

    if save_window_prediction_cache:
        if window_parts:
            window_cache_df = pd.concat(window_parts, ignore_index=True)
        else:
            window_cache_df = pd.DataFrame(columns=[
                "video_path",
                "eval_index",
                "eval_frame_idx",
                "ts_sec",
                "pred_label",
                "pred_conf",
                "target_prob",
            ])
        window_cache_df.to_csv(window_cache_path, index=False, encoding="utf-8")
        window_cache_meta_path.write_text(
            json.dumps(current_cache_meta, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    print("\n===== Batch Positive Sweep Summary =====")
    print(f"manifest_path: {safe_log_text(manifest_path)}")
    print(f"manifest_split: {manifest_split}")
    print(f"model_dir: {safe_log_text(model_dir)}")
    print(f"target_label: {target_label}")
    print(f"device: {device}")
    print(f"decode_backend: {decode_backend}")
    print(f"video_count: {len(video_files)}")
    print(f"cache_loaded: {cache_loaded}")
    print(f"cache_reused_videos: {reused_video_count}")
    print(f"newly_inferred_videos: {len(videos_to_infer)}")
    print(f"threshold_combinations: {len(combo_list)}")
    print(f"target_detection_rate_candidates: {detection_targets}")
    print(f"dynamic_min_hits_formula: ceil({history_window_count} * persistence_hit_ratio)")
    for _, row in recommended_df.iterrows():
        print(
            "recommended: "
            f"target={float(row['target_detection_rate']):.3f}, "
            f"clip_positive_threshold={float(row['clip_positive_threshold']):.3f}, "
            f"persistence_hit_ratio={float(row['persistence_hit_ratio']):.3f}, "
            f"detection_rate={float(row['detection_rate']):.6f}, "
            f"rule={row['recommendation_rule']}"
        )
    print(f"inference_stats_csv: {safe_log_text(infer_path)}")
    print(f"window_prediction_cache_csv: {safe_log_text(window_cache_path)}")
    print(f"window_prediction_cache_meta: {safe_log_text(window_cache_meta_path)}")
    print(f"per_video_per_threshold_csv: {safe_log_text(per_video_path)}")
    print(f"threshold_summary_csv: {safe_log_text(summary_path)}")
    print(f"recommended_threshold_csv: {safe_log_text(recommended_path)}")
    print(f"missed_videos_csv: {safe_log_text(missed_path)}")


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parent.parent

    run_mode = "batch_positive_sweep"  # "single_video" or "batch_positive_sweep"

    # Input / Output
    video_path = Path("../data/raw/sample.mp4")
    batch_manifest_path = project_root / "data" / "processed" / "01_assault_manifest_clips_downsampled.csv"
    batch_manifest_split = "test"
    model_dir = project_root / "outputs" / "01_assault_all_01_videomae_baseline" / "best_model"
    output_dir = project_root / "outputs" / "predictions" / "streaming_demo"

    # Sliding inference setup
    target_label = "assault"
    window_sec = 5.0
    step_sec = 2.0
    num_frames = 16
    sampling_rate = 4
    max_short_side = 360
    decode_thread_type = "AUTO"
    decode_backend = "ffmpeg_gpu"  # "ffmpeg_gpu" or "pyav_cpu"
    ffmpeg_path = "ffmpeg"

    # Persistence decision setup (Balanced) - only batch
    clip_positive_threshold = 0.50
    persistence_hit_ratio = 0.50
    # Persistence decision setup (Balanced) - common
    persistence_window_sec = 60.0
    clear_hit_ratio = 0.40
    min_history_sec = 30.0
    min_hits = math.ceil((math.floor(min_history_sec / step_sec) + 1) * persistence_hit_ratio)

    # Throughput setup
    decode_workers = 0
    infer_batch_size = 12
    infer_prefetch_factor = 2
    max_eval_windows = None  # set e.g. 80 for quick profiling

    # Batch positive-threshold sweep setup
    clip_positive_threshold_candidates = [0.35, 0.40, 0.45, 0.50, 0.55, 0.60]
    persistence_hit_ratio_candidates = [0.30, 0.35, 0.40, 0.45, 0.50]
    target_detection_rate_candidates = [0.85, 0.90, 0.95]
    reuse_window_prediction_cache = True
    save_window_prediction_cache = True

    if run_mode == "single_video":
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
            decode_backend=decode_backend,
            ffmpeg_path=ffmpeg_path,
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
    elif run_mode == "batch_positive_sweep":
        run_batch_positive_threshold_sweep(
            manifest_path=batch_manifest_path.resolve(),
            manifest_split=batch_manifest_split,
            model_dir=model_dir.resolve(),
            output_dir=(output_dir / "batch_positive_sweep").resolve(),
            target_label=target_label,
            window_sec=window_sec,
            step_sec=step_sec,
            num_frames=num_frames,
            sampling_rate=sampling_rate,
            max_short_side=max_short_side,
            decode_thread_type=decode_thread_type,
            decode_backend=decode_backend,
            ffmpeg_path=ffmpeg_path,
            persistence_window_sec=persistence_window_sec,
            clear_hit_ratio=clear_hit_ratio,
            min_history_sec=min_history_sec,
            min_hits=min_hits,
            decode_workers=decode_workers,
            infer_batch_size=infer_batch_size,
            infer_prefetch_factor=infer_prefetch_factor,
            max_eval_windows=max_eval_windows,
            clip_positive_threshold_candidates=clip_positive_threshold_candidates,
            persistence_hit_ratio_candidates=persistence_hit_ratio_candidates,
            target_detection_rate_candidates=target_detection_rate_candidates,
            reuse_window_prediction_cache=reuse_window_prediction_cache,
            save_window_prediction_cache=save_window_prediction_cache,
        )
    else:
        raise ValueError(
            f"Unsupported run_mode: {run_mode}. Expected 'single_video' or 'batch_positive_sweep'."
        )
