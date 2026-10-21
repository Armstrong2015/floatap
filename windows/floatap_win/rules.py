from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional

from .models import DisplaySnapshot, Step

MIN_INTERVAL_MS = 50
MAX_INTERVAL_MS = 3_600_000
MAX_JOBS = 50


class DraftIssue(str, Enum):
    NAME_EMPTY = "写一个作业名称"
    NO_POINT = "先选定点击位置"
    INTERVAL = "间隔要在 50 毫秒到 1 小时之间"
    INTERVAL_MAX = "填写最大间隔"
    INTERVAL_ORDER = "最大间隔不能小于最小间隔"
    NO_STOP = "填写持续时长或点击次数，避免一直点下去"
    COUNTDOWN = "倒计时要在 0 到 10 秒"
    TOO_MANY = "作业已有 50 个，先删除一些"
    LOOP = "循环次数要在 1 到 9999"
    NO_STEPS = "至少要有一步"


@dataclass
class StepDraft:
    name: str
    x: int
    y: int
    display_width: int
    display_height: int
    rotation: int
    interval_ms: int
    interval_max_ms: Optional[int] = None


@dataclass
class CheckedStep:
    name: str
    interval_ms: int
    interval_max_ms: int
    adjusted: bool


@dataclass
class JobDraft:
    name: str
    duration_sec: Optional[int]
    click_limit: Optional[int]
    countdown_sec: int
    pause_on_user_touch: bool
    existing_count: int
    is_new: bool
    steps: list[StepDraft]
    loop_limit: Optional[int] = None
    allow_missing_point: bool = False


@dataclass
class CheckedDraft:
    issue: Optional[DraftIssue]
    issue_step: Optional[int]
    interval_adjusted: bool
    duration_ms: Optional[int]
    steps: list[CheckedStep]
    loop_limit: Optional[int]

    def message(self) -> Optional[str]:
        if self.issue is None:
            return None
        if self.issue_step is None:
            return self.issue.value
        return f"第 {self.issue_step + 1} 步：{self.issue.value}"


def check_draft(draft: JobDraft) -> CheckedDraft:
    checked = [check_step(step, draft.allow_missing_point) for step in draft.steps]
    failed = next((i for i, item in enumerate(checked) if item[0] is not None), -1)
    duration_ms = draft.duration_sec * 1000 if draft.duration_sec and draft.duration_sec > 0 else None
    loop = draft.loop_limit
    issue: Optional[DraftIssue]
    if draft.is_new and draft.existing_count >= MAX_JOBS:
        issue = DraftIssue.TOO_MANY
    elif not draft.name.strip():
        issue = DraftIssue.NAME_EMPTY
    elif not draft.steps:
        issue = DraftIssue.NO_STEPS
    elif failed >= 0:
        issue = checked[failed][0]
    elif loop is not None and not (1 <= loop <= 9999):
        issue = DraftIssue.LOOP
    elif draft.countdown_sec not in range(0, 11):
        issue = DraftIssue.COUNTDOWN
    elif duration_ms is None and draft.click_limit is None and loop is None:
        issue = DraftIssue.NO_STOP
    else:
        issue = None
    return CheckedDraft(
        issue=issue,
        issue_step=failed if failed >= 0 and issue == checked[failed][0] else None,
        interval_adjusted=any(item[1].adjusted for item in checked) and issue is None,
        duration_ms=duration_ms,
        steps=[item[1] for item in checked],
        loop_limit=loop if loop is not None and 1 <= loop <= 9999 else None,
    )


def check_step(step: StepDraft, allow_missing_point: bool) -> tuple[Optional[DraftIssue], CheckedStep]:
    adjusted = 0 <= step.interval_ms < MIN_INTERVAL_MS
    interval = MIN_INTERVAL_MS if adjusted else step.interval_ms
    max_raw = step.interval_max_ms
    max_adjusted = max_raw is not None and 0 <= max_raw < MIN_INTERVAL_MS
    if max_raw is None:
        interval_max = interval
    elif max_adjusted:
        interval_max = MIN_INTERVAL_MS
    else:
        interval_max = max_raw
    issue: Optional[DraftIssue]
    if not allow_missing_point and (
        step.x < 0 or step.y < 0 or step.display_width <= 0 or step.display_height <= 0
    ):
        issue = DraftIssue.NO_POINT
    elif step.interval_ms < 0 or step.interval_ms > MAX_INTERVAL_MS:
        issue = DraftIssue.INTERVAL
    elif max_raw is not None and max_raw < 0:
        issue = DraftIssue.INTERVAL_MAX
    elif max_raw is not None and max_raw > MAX_INTERVAL_MS:
        issue = DraftIssue.INTERVAL
    elif max_raw is not None and interval_max < interval:
        issue = DraftIssue.INTERVAL_ORDER
    else:
        issue = None
    return issue, CheckedStep(
        name=step.name.strip() or "点击",
        interval_ms=interval,
        interval_max_ms=interval_max,
        adjusted=adjusted or max_adjusted,
    )


def display_matches(step: Step, current: DisplaySnapshot) -> bool:
    return (
        step.display_width == current.width
        and step.display_height == current.height
        and step.rotation == current.rotation
    )
