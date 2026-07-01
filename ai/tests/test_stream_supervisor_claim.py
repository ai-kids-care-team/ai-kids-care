"""
Unit tests for the claim/lease-driven supervisor wiring (shard-live-detection-deployments design
D2, tasks 3.1-3.5).

Covers, all with injectable mocks (no real subprocesses, no PyAV/torch, no network, no GPU/real
stream):
- ``make_claim_based_lister``: adapts a claim function into the zero-arg ``list_active_streams``
  shape, feeding it the supervisor's *current* running ids (renewal) and propagating a claim
  failure unchanged (so ``StreamSupervisor._desired()`` keeps the existing workers and retries).
- Full ``StreamSupervisor`` + claim-based lister integration: claim -> spawn, lease renewed ->
  worker kept, lease lost (stream drops out of ``assigned``) -> worker stopped, claim failure ->
  workers kept and retried next poll (never crashes), over-``MAX_WORKERS`` assigned set -> still
  WARN-and-cap (never silently exceeds capacity).
- ``_load_config_from_env``: fail-fast for both ``AI_SERVICE_TOKEN`` and ``DEPLOYMENT_ID`` missing,
  and the ``STREAM_POLL_INTERVAL_SEC`` default is 20 (design OQ-1: poll 20s / lease TTL 60s).
"""

from __future__ import annotations

import os as _os
import sys

_AI_SRC = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "..", "src"))
if _AI_SRC not in sys.path:
    sys.path.insert(0, _AI_SRC)

for _pkg in list(sys.modules.keys()):
    if _pkg == "ai_app" or _pkg.startswith("ai_app."):
        del sys.modules[_pkg]

import pytest  # noqa: E402

from ai_app.supervisor import (  # noqa: E402
    DEFAULT_POLL_INTERVAL_SEC,
    StreamSupervisor,
    _load_config_from_env,
    make_claim_based_lister,
)


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


def _stream(sid, model_id=1, kg=10):
    return {"streamId": sid, "modelId": model_id, "kindergartenId": kg}


# ---------------------------------------------------------------------------
# make_claim_based_lister
# ---------------------------------------------------------------------------

def test_lister_passes_current_running_ids_to_claim_fn():
    calls = []

    def claim_fn(running):
        calls.append(list(running))
        return [_stream(1)]

    lister = make_claim_based_lister(get_running_ids=lambda: [5, 6], claim_fn=claim_fn)
    result = lister()

    assert calls == [[5, 6]]
    assert result == [_stream(1)]


def test_lister_reads_running_ids_fresh_on_every_call():
    running_box = {"ids": []}
    calls = []

    def claim_fn(running):
        calls.append(list(running))
        return []

    lister = make_claim_based_lister(get_running_ids=lambda: running_box["ids"], claim_fn=claim_fn)
    lister()
    running_box["ids"] = [1, 2, 3]
    lister()

    assert calls == [[], [1, 2, 3]]


def test_lister_propagates_claim_fn_failure():
    def claim_fn(running):
        raise RuntimeError("backend unreachable")

    lister = make_claim_based_lister(get_running_ids=lambda: [], claim_fn=claim_fn)
    with pytest.raises(RuntimeError, match="backend unreachable"):
        lister()


# ---------------------------------------------------------------------------
# Full StreamSupervisor + claim-based lister integration
# ---------------------------------------------------------------------------

def _make_claim_supervisor(claim_fn, *, max_workers=8):
    spawned: list[FakeProc] = []

    def spawn(info):
        proc = FakeProc()
        spawned.append(proc)
        return proc

    sup = StreamSupervisor(
        list_active_streams=lambda: [],
        spawn_worker=spawn,
        max_workers=max_workers,
        logger=lambda _m: None,
    )
    sup.set_stream_source(
        make_claim_based_lister(get_running_ids=lambda: list(sup.workers.keys()), claim_fn=claim_fn)
    )
    return sup, spawned


def test_claim_assigns_streams_and_starts_workers():
    def claim_fn(running):
        assert running == []  # cold start
        return [_stream(1), _stream(2)]

    sup, spawned = _make_claim_supervisor(claim_fn)
    sup.reconcile()

    assert set(sup.workers) == {1, 2}
    assert len(spawned) == 2
    assert all(p.started for p in spawned)


def test_claim_renews_lease_running_ids_reflect_started_workers():
    running_seen = []

    def claim_fn(running):
        running_seen.append(list(running))
        # backend renews the running set unchanged + no new capacity
        return [_stream(sid) for sid in running] if running else [_stream(1)]

    sup, spawned = _make_claim_supervisor(claim_fn)
    sup.reconcile()  # cold start: claims stream 1
    assert set(sup.workers) == {1}

    sup.reconcile()  # second poll: renews with running=[1]
    assert running_seen == [[], [1]]
    assert set(sup.workers) == {1}
    # renewed stream's worker is left untouched (not respawned)
    assert len(spawned) == 1


def test_lost_lease_stops_worker_without_touching_others():
    state = {"assigned": [_stream(1), _stream(2)]}

    def claim_fn(running):
        return list(state["assigned"])

    sup, spawned = _make_claim_supervisor(claim_fn)
    sup.reconcile()
    assert set(sup.workers) == {1, 2}
    worker2_proc = sup.workers[2].process

    # stream 1's lease is lost to another deployment (or the stream became inactive) — the next
    # claim response no longer includes it.
    state["assigned"] = [_stream(2)]
    sup.reconcile()

    assert set(sup.workers) == {2}
    assert sup.workers[2].process is worker2_proc  # untouched
    assert len(spawned) == 2  # no respawn for the still-leased stream


def test_claim_failure_keeps_workers_and_retries_next_poll_without_crashing():
    state = {"assigned": [_stream(1)], "fail": False}

    def claim_fn(running):
        if state["fail"]:
            raise RuntimeError("backend unreachable")
        return list(state["assigned"])

    sup, spawned = _make_claim_supervisor(claim_fn)
    sup.reconcile()
    assert set(sup.workers) == {1}

    state["fail"] = True
    sup.reconcile()  # must not raise / crash
    assert set(sup.workers) == {1}  # kept as-is

    state["fail"] = False
    sup.reconcile()  # recovers on next successful poll
    assert set(sup.workers) == {1}
    assert len(spawned) == 1  # never respawned across the failure


def test_over_capacity_assigned_set_still_capped_with_warn():
    warnings = []

    def claim_fn(running):
        # Simulate a defensive scenario where the backend hands back more than capacity
        # (should not happen given the claim algorithm, but the local pool must never silently
        # exceed MAX_WORKERS regardless of what the source returns).
        return [_stream(1), _stream(2), _stream(3)]

    sup, spawned = _make_claim_supervisor(claim_fn, max_workers=2)
    sup._log = warnings.append  # capture WARN lines
    sup.reconcile()

    assert len(sup.workers) == 2
    assert len(spawned) == 2
    assert any("MAX_WORKERS=2" in w for w in warnings)


# ---------------------------------------------------------------------------
# _load_config_from_env
# ---------------------------------------------------------------------------

def test_config_fails_fast_when_ai_service_token_missing():
    with pytest.raises(ValueError, match="AI_SERVICE_TOKEN"):
        _load_config_from_env({"DEPLOYMENT_ID": "dep-a"})


def test_config_fails_fast_when_deployment_id_missing():
    with pytest.raises(ValueError, match="DEPLOYMENT_ID"):
        _load_config_from_env({"AI_SERVICE_TOKEN": "tok"})


def test_config_resolves_defaults_including_20s_poll_interval():
    config = _load_config_from_env({"AI_SERVICE_TOKEN": "tok", "DEPLOYMENT_ID": "dep-a"})
    assert config.token == "tok"
    assert config.deployment_id == "dep-a"
    assert config.backend_url == "http://backend:8080"
    assert config.poll_interval_sec == DEFAULT_POLL_INTERVAL_SEC == 20.0
    assert config.max_workers == 8


def test_config_honors_overrides():
    config = _load_config_from_env({
        "AI_SERVICE_TOKEN": "tok",
        "DEPLOYMENT_ID": "dep-b",
        "JAVA_BACKEND_URL": "http://custom:9000",
        "MAX_WORKERS": "3",
        "STREAM_POLL_INTERVAL_SEC": "15",
    })
    assert config.backend_url == "http://custom:9000"
    assert config.max_workers == 3
    assert config.poll_interval_sec == 15.0
