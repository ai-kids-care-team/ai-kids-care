#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
MAX_WORKERS capacity benchmark sampler (shard-live-detection-deployments design D4, tasks 4.1/4.2).

**Not run in CI / by this change** — no GPU is available here. This is the tool the maintainer
runs *on the target GPU host* to determine the right ``MAX_WORKERS`` (= the claim ``capacity``
sent to the backend) for that machine. See ``ai/docs/max-workers-benchmark-runbook.md`` for the
full step-by-step procedure and the judgement rule; this script only automates the repetitive
part of that procedure (sampling ``nvidia-smi`` at a fixed interval into a CSV row per sample).

Usage (on the GPU host, once the live-detection stack is already running with ``MAX_WORKERS=N``,
see the runbook for how to bring the stack up with real or replayed streams)::

    python scripts/benchmark_max_workers.py --label N=4 --duration-sec 300 \\
        --output-csv benchmarks/max_workers_N4.csv

Requires ``nvidia-smi`` on PATH (part of the NVIDIA driver install, not this repo's dependencies).
Deliberately has **no** ``ai_app``/torch/PyAV import — it is a standalone process-monitoring
script, importable and arg-parseable without any of this repo's ML dependencies installed.
"""

from __future__ import annotations

import argparse
import csv
import subprocess
import sys
import time
from pathlib import Path

# One row per GPU per sample. `utilization.gpu` / `memory.used` / `memory.total` are the fields the
# runbook's judgement rule reads; `timestamp` lets samples be correlated against the AI service's
# own logs (detection latency / dropped-frame warnings) after the run.
NVIDIA_SMI_QUERY_FIELDS = [
    "timestamp",
    "index",
    "utilization.gpu",
    "memory.used",
    "memory.total",
]


def _sample_nvidia_smi() -> list[dict[str, str]]:
    """Return one dict per GPU from a single ``nvidia-smi --query-gpu`` call.

    Raises:
        FileNotFoundError: if ``nvidia-smi`` is not on PATH (i.e. this is not a GPU host — run the
            benchmark on the target machine, not in CI or a CPU dev box).
        subprocess.CalledProcessError: if ``nvidia-smi`` exits non-zero.
    """
    fields = ",".join(NVIDIA_SMI_QUERY_FIELDS)
    result = subprocess.run(  # noqa: S603 — fixed argv, no shell, operator-invoked benchmark tool
        ["nvidia-smi", f"--query-gpu={fields}", "--format=csv,noheader,nounits"],
        capture_output=True,
        text=True,
        check=True,
        timeout=10,
    )
    rows = []
    for line in result.stdout.strip().splitlines():
        values = [v.strip() for v in line.split(",")]
        rows.append(dict(zip(NVIDIA_SMI_QUERY_FIELDS, values)))
    return rows


def run_benchmark(*, label: str, duration_sec: float, interval_sec: float, output_csv: Path) -> None:
    """Sample ``nvidia-smi`` every ``interval_sec`` for ``duration_sec``, writing rows to a CSV.

    Each row is tagged with ``label`` (the runbook convention: the ``MAX_WORKERS`` value under
    test, e.g. ``"N=4"``) so multiple runs (one per candidate N) can be concatenated and compared.
    Appends to ``output_csv`` if it already exists (header written only once).
    """
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    write_header = not output_csv.exists()

    with output_csv.open("a", newline="") as f:
        writer = csv.writer(f)
        if write_header:
            writer.writerow(["label", *NVIDIA_SMI_QUERY_FIELDS])

        elapsed = 0.0
        samples_taken = 0
        while elapsed < duration_sec:
            for row in _sample_nvidia_smi():
                writer.writerow([label] + [row[field] for field in NVIDIA_SMI_QUERY_FIELDS])
            f.flush()
            samples_taken += 1
            time.sleep(interval_sec)
            elapsed += interval_sec

        print(
            f"[benchmark] label={label} samples={samples_taken} "
            f"duration_sec={duration_sec} -> {output_csv}"
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--label", required=True,
        help="Tag for this run's rows, by convention the MAX_WORKERS value under test (e.g. 'N=4').",
    )
    parser.add_argument(
        "--duration-sec", type=float, default=300.0,
        help="How long to sample (seconds). Should span a representative mix of quiet/alert "
             "windows for the streams under test (default 300s).",
    )
    parser.add_argument(
        "--interval-sec", type=float, default=5.0,
        help="Seconds between nvidia-smi samples (default 5s).",
    )
    parser.add_argument(
        "--output-csv", type=Path, required=True,
        help="CSV path to append rows to (created if absent).",
    )
    args = parser.parse_args(argv)

    try:
        run_benchmark(
            label=args.label,
            duration_sec=args.duration_sec,
            interval_sec=args.interval_sec,
            output_csv=args.output_csv,
        )
    except FileNotFoundError:
        print(
            "[ERROR] nvidia-smi not found on PATH — this benchmark must run on the target GPU "
            "host (NVIDIA driver installed), not in CI or a CPU dev environment.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":  # pragma: no cover — operator-invoked on a GPU host, not exercised in CI
    raise SystemExit(main())
