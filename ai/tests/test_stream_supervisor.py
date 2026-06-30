"""
Unit tests for ai_app.supervisor.StreamSupervisor (multi-camera supervisor, design D2).

All collaborators are injected — no real subprocesses, no PyAV/torch, no backend, no streams. A
``FakeProc`` stands in for a worker process and a mutable ``clock`` drives the restart backoff gate.
Covers the three spec scenarios: one worker per active stream, crashed worker restarted without
stopping the others, and active-set change adds/removes workers — plus the MAX_WORKERS cap and the
best-effort behaviour when stream enumeration fails.
"""

from __future__ import annotations

import os as _os
import sys

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

# Drop any MagicMock stubs a prior test may have installed for ai_app.*
for _pkg in list(sys.modules.keys()):
    if _pkg == "ai_app" or _pkg.startswith("ai_app."):
        del sys.modules[_pkg]

from ai_app.supervisor import StreamSupervisor  # noqa: E402


class FakeProc:
    """Minimal process stand-in: start/is_alive/terminate, plus a `die()` test helper."""

    def __init__(self) -> None:
        self.started = False
        self.terminated = False
        self._alive = True

    def start(self) -> None:
        self.started = True

    def is_alive(self) -> bool:
        return self._alive

    def terminate(self) -> None:
        self.terminated = True
        self._alive = False

    def die(self) -> None:
        self._alive = False


class _Clock:
    """Mutable monotonic clock for driving the restart backoff gate deterministically."""

    def __init__(self, t: float = 0.0) -> None:
        self.t = t

    def __call__(self) -> float:
        return self.t


def _make(streams, *, max_workers=8, backoff=5.0, clock=None):
    """Build a supervisor over a mutable `streams` list; spawn returns a fresh FakeProc each call."""
    spawned: list[FakeProc] = []

    def spawn(info):
        proc = FakeProc()
        spawned.append(proc)
        return proc

    sup = StreamSupervisor(
        list_active_streams=lambda: list(streams),
        spawn_worker=spawn,
        max_workers=max_workers,
        restart_backoff_sec=backoff,
        clock=clock or _Clock(),
        logger=lambda _m: None,
    )
    return sup, spawned


def _stream(sid, model_id=1, kg=10):
    return {"streamId": sid, "modelId": model_id, "kindergartenId": kg}


def test_one_worker_per_active_stream():
    sup, spawned = _make([_stream(1), _stream(2), _stream(3)])
    sup.reconcile()
    assert set(sup.workers) == {1, 2, 3}
    assert len(spawned) == 3
    assert all(p.started for p in spawned)


def test_idempotent_reconcile_does_not_respawn_alive_workers():
    sup, spawned = _make([_stream(1), _stream(2)])
    sup.reconcile()
    procs = {sid: w.process for sid, w in sup.workers.items()}
    sup.reconcile()  # nothing changed → no new spawns
    assert len(spawned) == 2
    assert {sid: w.process for sid, w in sup.workers.items()} == procs


def test_respects_max_workers_cap():
    sup, spawned = _make([_stream(1), _stream(2), _stream(3)], max_workers=2)
    sup.reconcile()
    assert len(sup.workers) == 2
    assert len(spawned) == 2


def test_crashed_worker_restarted_after_backoff_without_touching_others():
    clock = _Clock(0.0)
    sup, spawned = _make([_stream(1), _stream(2)], backoff=5.0, clock=clock)
    sup.reconcile()
    assert len(spawned) == 2
    worker2_proc = sup.workers[2].process  # the healthy one — must be left untouched

    # stream 1's worker crashes
    sup.workers[1].process.die()

    # before the backoff gate elapses, no restart happens
    clock.t = 3.0
    sup.reconcile()
    assert len(spawned) == 2

    # after the backoff gate, the dead worker is restarted; the healthy one is untouched
    clock.t = 10.0
    sup.reconcile()
    assert len(spawned) == 3
    assert sup.workers[1].process is spawned[2]      # replaced with the new process
    assert sup.workers[1].process.is_alive()
    assert sup.workers[2].process is worker2_proc     # unaffected


def test_active_set_change_adds_and_removes_workers():
    streams = [_stream(1), _stream(2)]
    sup, spawned = _make(streams)
    sup.reconcile()
    worker2_proc = sup.workers[2].process

    # stream 1 removed, stream 3 added; stream 2 stays
    streams.clear()
    streams.extend([_stream(2), _stream(3)])
    sup.reconcile()

    assert set(sup.workers) == {2, 3}
    assert sup.workers[2].process is worker2_proc  # untouched
    assert len(spawned) == 3                       # 1,2 then 3


def test_removed_stream_worker_is_terminated():
    streams = [_stream(1)]
    sup, _ = _make(streams)
    sup.reconcile()
    proc1 = sup.workers[1].process
    streams.clear()
    sup.reconcile()
    assert 1 not in sup.workers
    assert proc1.terminated is True


def test_enumeration_failure_keeps_existing_workers():
    # First a successful reconcile establishes two workers.
    state = {"raise": False, "streams": [_stream(1), _stream(2)]}

    def lister():
        if state["raise"]:
            raise RuntimeError("backend unreachable")
        return list(state["streams"])

    spawned: list[FakeProc] = []

    def spawn(info):
        p = FakeProc()
        spawned.append(p)
        return p

    sup = StreamSupervisor(
        list_active_streams=lister,
        spawn_worker=spawn,
        logger=lambda _m: None,
    )
    sup.reconcile()
    assert set(sup.workers) == {1, 2}

    # Now enumeration fails — workers must be kept, none torn down, no crash.
    state["raise"] = True
    sup.reconcile()
    assert set(sup.workers) == {1, 2}
    assert len(spawned) == 2


def test_run_forever_bounded_iterations():
    streams = [_stream(1)]
    sup, spawned = _make(streams)
    sleeps: list[float] = []
    sup.run_forever(poll_interval_sec=1.0, sleeper=sleeps.append, max_iterations=3)
    # 3 reconciles, sleeps between (not after the last) → 2 sleeps
    assert len(sleeps) == 2
    assert set(sup.workers) == {1}
