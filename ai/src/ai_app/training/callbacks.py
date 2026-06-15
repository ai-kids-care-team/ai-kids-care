#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 20:15
@Author  : zhangjunfan1997@naver.com
@File    : callbacks
"""
from __future__ import annotations

import gc

import torch
from transformers import TrainerCallback


class MemoryCleanupCallback(TrainerCallback):
    """
    Periodically trigger host GC and optional CUDA cache cleanup.
    """

    def __init__(
            self,
            gc_every_n_steps: int = 0,
            empty_cuda_cache_every_n_steps: int = 0,
    ) -> None:
        self.gc_every_n_steps = max(0, int(gc_every_n_steps))
        self.empty_cuda_cache_every_n_steps = max(0, int(empty_cuda_cache_every_n_steps))

    def on_step_end(self, args, state, control, **kwargs):
        step = int(state.global_step)

        if self.gc_every_n_steps > 0 and step > 0 and step % self.gc_every_n_steps == 0:
            gc.collect()

        if (
                self.empty_cuda_cache_every_n_steps > 0
                and torch.cuda.is_available()
                and step > 0
                and step % self.empty_cuda_cache_every_n_steps == 0
        ):
            torch.cuda.empty_cache()

        return control
