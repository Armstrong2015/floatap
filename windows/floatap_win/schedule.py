from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Optional

from .models import StopReason


@dataclass
class Progress:
    clicks: int = 0
    loops: int = 0
    first_click_elapsed: Optional[int] = None


@dataclass
class PlannedClick:
    step_index: int
    at_ms: int


@dataclass
class ScheduleInput:
    waits_ms: list[int]
    countdown_sec: int = 3
    duration_ms: Optional[int] = None
    click_limit: Optional[int] = None
    loop_limit: Optional[int] = None


@dataclass
class ScheduleResult:
    clicks: list[PlannedClick]
    stop: StopReason
    finished_at_ms: int


def wait_millis(min_ms: int, max_ms: int, rng: random.Random) -> int:
    low = max(0, min_ms)
    high = max(low, max_ms)
    if high == low:
        return low
    return rng.randint(low, high)


def next_stop(
    duration_ms: Optional[int],
    click_limit: Optional[int],
    loop_limit: Optional[int],
    progress: Progress,
    now_elapsed: int,
) -> Optional[StopReason]:
    if click_limit is not None and progress.clicks >= click_limit:
        return StopReason.CLICK_LIMIT
    if loop_limit is not None and progress.loops >= loop_limit:
        return StopReason.LOOP_LIMIT
    first = progress.first_click_elapsed
    if duration_ms is not None and first is not None and now_elapsed - first >= duration_ms:
        return StopReason.DURATION
    return None


def schedule(input_data: ScheduleInput) -> ScheduleResult:
    if not input_data.waits_ms:
        raise ValueError("schedule needs at least one step")
    clicks: list[PlannedClick] = []
    t = max(0, input_data.countdown_sec) * 1000
    index = 0
    loops = 0
    progress = Progress()
    while True:
        t += input_data.waits_ms[index]
        reason = next_stop(
            input_data.duration_ms,
            input_data.click_limit,
            input_data.loop_limit,
            progress,
            t,
        )
        if reason is not None:
            return ScheduleResult(clicks, reason, t)
        clicks.append(PlannedClick(index, t))
        first = progress.first_click_elapsed if progress.first_click_elapsed is not None else t
        count = progress.clicks + 1
        wrapped = index + 1 >= len(input_data.waits_ms)
        next_index = 0 if wrapped else index + 1
        if wrapped:
            loops += 1
        progress = Progress(count, loops, first)
        index = next_index
        if (
            input_data.loop_limit is not None
            and loops >= input_data.loop_limit
            and next_index == 0
        ):
            return ScheduleResult(clicks, StopReason.LOOP_LIMIT, t)
        after = next_stop(
            input_data.duration_ms,
            input_data.click_limit,
            input_data.loop_limit,
            progress,
            t,
        )
        if after in (StopReason.CLICK_LIMIT, StopReason.DURATION):
            return ScheduleResult(clicks, after, t)
