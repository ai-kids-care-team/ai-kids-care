#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
VideoMAE label → backend ``event_type_enum`` mapping (closed-loop step ④).

Centralised in a single module per the ai-detection spec ("Event type label mapping"), so a
future Phase-2 move of this mapping to the Java backend is a single controlled change. Any
label not in the table maps to ``OTHER``.
"""

from __future__ import annotations

# AI Hub dataset (dataSetSn=171) label → event_type_enum value.
_LABEL_TO_EVENT_TYPE = {
    "assault": "ASSAULT",
    "fight": "FIGHT",
    "burglary": "BURGLARY",
    "vandalism": "VANDALISM",
    "swoon": "SWOON",
    "wander": "WANDER",
    "trespass": "TRESPASS",
    "dump": "DUMP",
    "robbery": "ROBBERY",
    "datefight": "DATEFIGHT",
    "kidnap": "KIDNAP",
    "drunken": "DRUNKEN",
}


def map_label(label) -> str:
    """Map a VideoMAE output label to an ``event_type_enum`` value; unknown/blank/None → ``OTHER``."""
    if label is None:
        return "OTHER"
    return _LABEL_TO_EVENT_TYPE.get(str(label).strip().lower(), "OTHER")
