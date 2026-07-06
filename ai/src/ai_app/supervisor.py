#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Multi-camera live-detection supervisor (ai-ingest design D2 / Scheme A execution model,
shard-live-detection-deployments design D1/D2 for cross-stack distribution).

Periodically claims/renews its share of active camera streams from the backend's Redis-backed
claim/lease endpoint (``POST /api/v1/internal/streams/claim``, see
``ai_app.utils.stream_claim.claim_streams`` and ``make_claim_based_lister``) and runs **one
detection worker subprocess per assigned stream** (the existing single-stream
``run_stream_service``). Process isolation means one stream's crash or memory leak does not
affect the others and sidesteps the GIL for parallel PyAV decode (design D2).

The supervisor core (this class) is intentionally **thin and ML-free at import time**: the heavy
PyAV/torch worker code is imported lazily, only inside the child process entrypoint. All
collaborators are injectable so the reconcile/restart/cap logic is unit-tested without real
subprocesses, streams, or a backend.

Responsibilities:
- start one worker per assigned stream, up to ``MAX_WORKERS`` (this deployment's claim
  ``capacity``);
- restart a worker that has exited, with a backoff gate (no tight crash loop);
- reconcile when the assigned-stream set changes (claimed, lost lease, or disabled upstream) —
  add new, stop removed, leave the rest untouched;
- never let one worker's failure (or a claim failure) terminate the supervisor — a claim failure
  is logged and the current worker set is kept until the next successful poll (also renews the
  lease, acting as a heartbeat).
"""

from __future__ import annotations

import os
import time
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional

DEFAULT_MAX_WORKERS = 8
DEFAULT_RESTART_BACKOFF_SEC = 5.0
# shard-live-detection-deployments design D2: claim doubles as heartbeat/lease-renewal; TTL
# (backend-side, default 60s) is sized as a multiple of this poll interval.
DEFAULT_POLL_INTERVAL_SEC = 20.0

# A stream descriptor as returned by the backend registry: {"streamId", "modelId", "kindergartenId"}.
StreamInfo = Dict[str, Any]


@dataclass
class _Worker:
    """Bookkeeping for one running detection worker subprocess."""
    stream_id: int
    info: StreamInfo
    process: Any  # anything with start()/is_alive()/terminate(); a multiprocessing.Process in prod
    restart_not_before: float = 0.0  # monotonic gate: do not restart before this instant


def _is_alive(process: Any) -> bool:
    try:
        return bool(process.is_alive())
    except Exception:  # noqa: BLE001 — a broken handle is treated as dead, never crashes reconcile
        return False


class StreamSupervisor:
    """Reconciles a set of detection worker subprocesses against the active-stream set."""

    def __init__(
        self,
        *,
        list_active_streams: Callable[[], List[StreamInfo]],
        spawn_worker: Callable[[StreamInfo], Any],
        max_workers: int = DEFAULT_MAX_WORKERS,
        restart_backoff_sec: float = DEFAULT_RESTART_BACKOFF_SEC,
        clock: Callable[[], float] = time.monotonic,
        logger: Callable[[str], None] = print,
    ) -> None:
        """
        Args:
            list_active_streams: Returns the current desired stream descriptors. May raise; a failure
                is treated as "keep the current set" (best-effort, never tears workers down).
            spawn_worker: Builds (but does NOT start) a process-like worker for a stream descriptor.
                The supervisor calls ``.start()`` itself.
            max_workers: Hard cap on concurrent workers (capacity protection, design Risk GPU/CPU).
            restart_backoff_sec: Minimum delay before a dead worker is restarted.
            clock: Monotonic clock source (injectable for tests).
            logger: Line logger (injectable; defaults to ``print``).
        """
        self._list_active_streams = list_active_streams
        self._spawn_worker = spawn_worker
        self._max_workers = max(1, int(max_workers))
        self._restart_backoff_sec = max(0.0, float(restart_backoff_sec))
        self._clock = clock
        self._log = logger
        self._workers: Dict[int, _Worker] = {}

    @property
    def workers(self) -> Dict[int, _Worker]:
        return self._workers

    def set_stream_source(self, list_active_streams: Callable[[], List[StreamInfo]]) -> None:
        """Replace the desired-stream-set source after construction.

        Used to wire the claim/lease-based lister (``make_claim_based_lister``), which needs a
        reference to *this* supervisor's currently-running worker ids (to send as ``running`` on
        each claim call) — a reference only available once the supervisor instance exists. Purely
        additive: the constructor-supplied source keeps working unchanged for callers/tests that
        never call this.
        """
        self._list_active_streams = list_active_streams

    def _desired(self) -> Dict[int, StreamInfo]:
        """Resolve the desired stream set; on enumeration failure keep the current workers' set."""
        try:
            raw = self._list_active_streams() or []
        except Exception as exc:  # noqa: BLE001 — best-effort: enumeration failure must not crash
            self._log(
                "[WARN] active-stream enumeration failed; keeping current workers: "
                f"{type(exc).__name__}: {exc}"
            )
            return {sid: w.info for sid, w in self._workers.items()}
        desired: Dict[int, StreamInfo] = {}
        for info in raw:
            desired[int(info["streamId"])] = info
        return desired

    def _spawn(self, info: StreamInfo) -> None:
        stream_id = int(info["streamId"])
        process = self._spawn_worker(info)
        try:
            process.start()
        except Exception as exc:  # noqa: BLE001 — a failed start is logged; reconcile retries later
            self._log(f"[WARN] worker start failed for stream {stream_id}: {type(exc).__name__}: {exc}")
            return
        self._workers[stream_id] = _Worker(
            stream_id=stream_id,
            info=info,
            process=process,
            restart_not_before=self._clock() + self._restart_backoff_sec,
        )
        self._log(f"[INFO] started detection worker for stream {stream_id}")

    def _stop(self, stream_id: int) -> None:
        worker = self._workers.pop(stream_id, None)
        if worker is None:
            return
        try:
            if _is_alive(worker.process):
                worker.process.terminate()
        except Exception as exc:  # noqa: BLE001
            self._log(f"[WARN] terminate failed for stream {stream_id}: {type(exc).__name__}: {exc}")
        self._log(f"[INFO] stopped detection worker for stream {stream_id}")

    def reconcile(self) -> None:
        """One reconcile pass: stop removed, restart dead (with backoff), start new (within cap)."""
        desired = self._desired()

        # 1) stop workers whose stream is no longer active
        for stream_id in list(self._workers):
            if stream_id not in desired:
                self._stop(stream_id)

        # 2) restart dead workers that are still desired (respecting the backoff gate); untouched
        #    workers (still alive) are left exactly as they are.
        for stream_id, worker in list(self._workers.items()):
            if stream_id in desired and not _is_alive(worker.process):
                if self._clock() >= worker.restart_not_before:
                    self._log(f"[INFO] restarting exited worker for stream {stream_id}")
                    self._spawn(desired[stream_id])

        # 3) start workers for newly active streams, up to the cap
        for stream_id, info in desired.items():
            if stream_id in self._workers:
                continue
            if len(self._workers) >= self._max_workers:
                self._log(
                    f"[WARN] MAX_WORKERS={self._max_workers} reached; not starting stream {stream_id}"
                )
                continue
            self._spawn(info)

    def run_forever(
        self,
        *,
        poll_interval_sec: float = DEFAULT_POLL_INTERVAL_SEC,
        sleeper: Callable[[float], None] = time.sleep,
        max_iterations: Optional[int] = None,
    ) -> None:
        """Reconcile on a fixed interval until ``max_iterations`` (``None`` = forever)."""
        iterations = 0
        while max_iterations is None or iterations < max_iterations:
            try:
                self.reconcile()
            except Exception as exc:  # noqa: BLE001 — a reconcile error must not stop the loop
                self._log(f"[WARN] reconcile error (continuing): {type(exc).__name__}: {exc}")
            iterations += 1
            if max_iterations is not None and iterations >= max_iterations:
                break
            sleeper(float(poll_interval_sec))

    def stop_all(self) -> None:
        for stream_id in list(self._workers):
            self._stop(stream_id)


def make_claim_based_lister(
    *,
    get_running_ids: Callable[[], List[int]],
    claim_fn: Callable[[List[int]], List[StreamInfo]],
) -> Callable[[], List[StreamInfo]]:
    """Build a zero-arg ``list_active_streams`` callable backed by the claim/lease endpoint.

    Adapts ``ai_app.utils.stream_claim.claim_streams`` (which needs a ``running`` list to renew
    leases) to the zero-arg ``list_active_streams`` shape ``StreamSupervisor`` expects, by pulling
    the *current* running stream ids from ``get_running_ids`` (typically
    ``lambda: list(supervisor.workers)``) on every call.

    ``claim_fn`` is expected to already be bound to ``deploymentId``/``capacity`` (a closure over
    ``claim_streams(backend_url, token, deployment_id, capacity, running)``); this factory only
    handles the "what are we running right now" plumbing so ``StreamSupervisor`` itself needs no
    claim-specific knowledge.

    A ``claim_fn`` failure (backend unreachable, non-2xx) propagates unchanged — the caller
    (``StreamSupervisor._desired()``) already treats a raised exception from the stream source as
    "keep the current workers, log, retry next poll" (design D2's "claim 失败不 crash"), so this
    function does not swallow errors itself.
    """

    def _lister() -> List[StreamInfo]:
        running = list(get_running_ids())
        return list(claim_fn(running))

    return _lister


@dataclass
class SupervisorConfig:
    """Resolved supervisor configuration (see ``_load_config_from_env``)."""
    backend_url: str
    token: str
    deployment_id: str
    max_workers: int
    poll_interval_sec: float


def _load_config_from_env(env: Optional[Dict[str, str]] = None) -> SupervisorConfig:
    """Resolve supervisor configuration from environment variables.

    Fail-fast (``ValueError``) on missing required secrets/identity — ``AI_SERVICE_TOKEN`` (shared
    auth) and ``DEPLOYMENT_ID`` (this stack's lease-ownership identity, shard-live-detection-
    deployments design D2) follow the same contract: absent or empty is a startup error, never a
    silent default.

    Args:
        env: Injectable environment mapping (defaults to ``os.environ``); lets tests exercise the
            fail-fast paths without mutating real process environment.
    """
    source = os.environ if env is None else env

    backend_url = source.get("JAVA_BACKEND_URL", "http://backend:8080")

    token = source.get("AI_SERVICE_TOKEN", "")
    if not token:
        raise ValueError("AI_SERVICE_TOKEN must be set for the live-detection supervisor")

    deployment_id = source.get("DEPLOYMENT_ID", "")
    if not deployment_id:
        raise ValueError("DEPLOYMENT_ID must be set for the live-detection supervisor")

    max_workers = int(source.get("MAX_WORKERS", str(DEFAULT_MAX_WORKERS)))
    poll_interval_sec = float(source.get("STREAM_POLL_INTERVAL_SEC", str(DEFAULT_POLL_INTERVAL_SEC)))

    return SupervisorConfig(
        backend_url=backend_url,
        token=token,
        deployment_id=deployment_id,
        max_workers=max_workers,
        poll_interval_sec=poll_interval_sec,
    )


# ---------------------------------------------------------------------------
# Production wiring (lazy ML import — only runs in the child process)
# ---------------------------------------------------------------------------

def _worker_entry(info: StreamInfo) -> None:  # pragma: no cover — runs only in a child process
    """Child-process entrypoint: fetch credentials for one stream and run its alert service.

    ARC-02 (harden-ai-serving, C3): ``run_stream_service`` is loaded via a normal package
    import (``ai_app.live.alert_service``), not ``importlib`` file-path loading. The import
    stays function-scoped (not at module level) so importing ``ai_app.supervisor`` never
    pulls in the ML-heavy alert service module (and its av/torch/transformers deps) — only
    a spawned child process actually needing it does.
    """
    import pathlib

    from ai_app.live.alert_service import run_stream_service
    from ai_app.utils.stream_credentials import build_stream_url, fetch_stream_credentials

    stream_id = str(info["streamId"])
    model_id = info.get("modelId") or int(os.getenv("MODEL_ID", "1"))
    backend_url = os.getenv("JAVA_BACKEND_URL", "http://backend:8080")
    token = os.getenv("AI_SERVICE_TOKEN", "")
    if not token:
        raise ValueError("AI_SERVICE_TOKEN must be set for the live-detection worker")
    deployment_id = os.getenv("DEPLOYMENT_ID", "")
    if not deployment_id:
        raise ValueError("DEPLOYMENT_ID must be set for the live-detection worker")

    # X-Deployment-Id lets the backend verify this stack currently holds the stream's
    # claim/lease before releasing credentials (defense-in-depth, design Risk "凭据端点越权").
    cred = fetch_stream_credentials(stream_id, backend_url, token, deployment_id=deployment_id)
    stream_url = build_stream_url(cred)

    project_root = pathlib.Path(__file__).resolve().parents[2]
    model_dir = project_root / "outputs" / "01_assault_videomae_baseline" / "best_model"
    output_dir = project_root / "outputs" / "predictions" / f"stream_live_service_{stream_id}"

    run_stream_service(
        stream_url=stream_url,
        model_dir=model_dir.resolve(),
        output_dir=output_dir.resolve(),
        stream_id=stream_id,
        model_id=int(model_id),
        java_backend_url=backend_url,
        ai_service_token=token,
    )


def _default_spawn_worker(info: StreamInfo) -> Any:  # pragma: no cover — needs a real OS process
    """Build (not start) a spawn-context subprocess running ``_worker_entry`` for one stream."""
    import multiprocessing

    ctx = multiprocessing.get_context("spawn")
    return ctx.Process(
        target=_worker_entry,
        args=(info,),
        name=f"stream-worker-{info['streamId']}",
        daemon=False,
    )


def main() -> None:  # pragma: no cover — process entrypoint, exercised by integration only
    """Long-lived supervisor entrypoint (the deployed live-detection service).

    Desired-stream source is the claim/lease endpoint (design D2): each poll, claim renews the
    lease for the streams currently running here and claims spare capacity, returning the
    complete ``assigned`` set this deployment should run this round; the existing bounded-pool
    ``reconcile()`` (add new / stop removed / restart dead, capped at ``MAX_WORKERS``) is reused
    unchanged.
    """
    config = _load_config_from_env()

    from ai_app.utils.stream_claim import claim_streams

    supervisor = StreamSupervisor(
        # Placeholder until replaced by set_stream_source() below — make_claim_based_lister
        # needs a reference to this supervisor instance (to read its running worker ids), which
        # only exists after construction.
        list_active_streams=lambda: [],
        spawn_worker=_default_spawn_worker,
        max_workers=config.max_workers,
    )
    supervisor.set_stream_source(
        make_claim_based_lister(
            get_running_ids=lambda: list(supervisor.workers.keys()),
            claim_fn=lambda running: claim_streams(
                config.backend_url, config.token, config.deployment_id, config.max_workers, running
            ),
        )
    )
    print(
        f"[INFO] live-detection supervisor starting: backend={config.backend_url}, "
        f"deployment_id={config.deployment_id}, max_workers={config.max_workers}, "
        f"poll_interval_sec={config.poll_interval_sec}"
    )
    try:
        supervisor.run_forever(poll_interval_sec=config.poll_interval_sec)
    finally:
        supervisor.stop_all()


if __name__ == "__main__":
    main()
