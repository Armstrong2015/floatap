from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Optional


class WaitMode(str, Enum):
    FIXED = "fixed"
    RANGE = "range"


class RunPhase(str, Enum):
    IDLE = "idle"
    ARMED = "armed"
    RUNNING = "running"
    PAUSED = "paused"


class StopReason(str, Enum):
    USER = "user"
    DURATION = "duration"
    CLICK_LIMIT = "click_limit"
    LOOP_LIMIT = "loop_limit"
    DISPLAY_CHANGED = "display_changed"


@dataclass
class Step:
    order: int
    name: str
    x: int
    y: int
    display_width: int
    display_height: int
    rotation: int = 0
    wait_mode: WaitMode = WaitMode.FIXED
    wait_min_ms: int = 500
    wait_max_ms: int = 500

    def to_json(self) -> dict[str, Any]:
        mode = WaitMode.RANGE if self.wait_max_ms > self.wait_min_ms else WaitMode.FIXED
        return {
            "name": self.name,
            "x": self.x,
            "y": self.y,
            "displayWidth": self.display_width,
            "displayHeight": self.display_height,
            "rotation": self.rotation,
            "wait": {
                "mode": mode.value,
                "minMs": self.wait_min_ms,
                "maxMs": self.wait_max_ms if mode == WaitMode.RANGE else self.wait_min_ms,
            },
        }

    @staticmethod
    def from_json(order: int, raw: dict[str, Any]) -> "Step":
        wait = raw.get("wait") or {}
        min_ms = int(wait.get("minMs", 500))
        max_ms = int(wait.get("maxMs", min_ms))
        mode_raw = str(wait.get("mode", "fixed")).lower()
        mode = WaitMode.RANGE if mode_raw == "range" and max_ms > min_ms else WaitMode.FIXED
        return Step(
            order=order,
            name=str(raw.get("name") or "点击"),
            x=int(raw.get("x", -1)),
            y=int(raw.get("y", -1)),
            display_width=int(raw.get("displayWidth", 0)),
            display_height=int(raw.get("displayHeight", 0)),
            rotation=int(raw.get("rotation", 0)),
            wait_mode=mode,
            wait_min_ms=min_ms,
            wait_max_ms=max_ms if mode == WaitMode.RANGE else min_ms,
        )


@dataclass
class Job:
    id: str
    name: str
    steps: list[Step]
    duration_ms: Optional[int] = None
    click_limit: Optional[int] = None
    loop_limit: Optional[int] = None
    countdown_sec: int = 3
    pause_on_user_touch: bool = False
    updated_at: int = 0

    def to_json(self) -> dict[str, Any]:
        return {
            "format": 1,
            "id": self.id,
            "name": self.name,
            "countdownSec": self.countdown_sec,
            "pauseOnUserTouch": self.pause_on_user_touch,
            "durationMs": self.duration_ms,
            "clickLimit": self.click_limit,
            "loopLimit": self.loop_limit,
            "scheduleStart": None,
            "scheduleEnd": None,
            "updatedAt": self.updated_at,
            "steps": [step.to_json() for step in self.steps],
        }

    @staticmethod
    def from_json(raw: dict[str, Any], fallback_id: str) -> "Job":
        if int(raw.get("format", 0)) != 1:
            raise ValueError("unsupported format")
        steps_raw = raw.get("steps") or []
        steps = [Step.from_json(i, item) for i, item in enumerate(steps_raw)]
        return Job(
            id=str(raw.get("id") or fallback_id),
            name=str(raw.get("name") or "作业"),
            steps=steps,
            duration_ms=_opt_int(raw.get("durationMs")),
            click_limit=_opt_int(raw.get("clickLimit")),
            loop_limit=_opt_int(raw.get("loopLimit")),
            countdown_sec=int(raw.get("countdownSec", 3)),
            pause_on_user_touch=bool(raw.get("pauseOnUserTouch", False)),
            updated_at=int(raw.get("updatedAt", 0)),
        )


@dataclass
class JobSummary:
    id: str
    name: str
    step_count: int
    updated_at: int


@dataclass
class DisplaySnapshot:
    width: int
    height: int
    rotation: int = 0


@dataclass
class Prefs:
    boundary_accepted: bool = False
    last_job_id: str = ""
    last_step_index: int = 0
    ball_x: int = 24
    ball_y: int = 240


def _opt_int(value: Any) -> Optional[int]:
    if value is None or value == "":
        return None
    return int(value)
