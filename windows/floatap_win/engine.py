from __future__ import annotations

import random
import threading
import time
from typing import Callable, Optional

from . import click
from .display import read_display
from .hook import MousePauseWatcher
from .hotkeys import HotkeyWatcher
from .models import Job, RunPhase, StopReason
from .rules import display_matches
from .schedule import Progress, next_stop, wait_millis


class RunEngine:
    def __init__(
        self,
        on_phase: Callable[[RunPhase], None],
        on_message: Callable[[str], None],
        on_click: Optional[Callable[[int, int, int], None]] = None,
    ) -> None:
        self._on_phase = on_phase
        self._on_message = on_message
        self._on_click = on_click
        self.phase = RunPhase.IDLE
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._pause = threading.Event()
        self._pause.set()
        self._lock = threading.Lock()
        self._watcher = MousePauseWatcher(self._user_touch)
        self._watcher.start()
        self._hotkeys = HotkeyWatcher(self._hotkey_pause_toggle, self._hotkey_stop)

    def shutdown(self) -> None:
        self.stop(StopReason.USER)
        self._watcher.stop()
        self._hotkeys.enabled = False

    def start(self, job: Job) -> None:
        with self._lock:
            if self.phase != RunPhase.IDLE:
                return
            if not job.steps:
                self._on_message("至少要有一步")
                return
            for i, step in enumerate(job.steps):
                if step.x < 0 or step.y < 0:
                    self._on_message(f"第 {i + 1} 步：先选定点击位置")
                    return
            display = read_display()
            for step in job.steps:
                if not display_matches(step, display):
                    self._on_message("屏幕尺寸和标定时不一致，请重新选点")
                    return
            self._stop.clear()
            self._pause.set()
            self._set_phase(RunPhase.ARMED)
            self._watcher.enabled = bool(job.pause_on_user_touch)
            self._watcher.ignore_until = time.time() + 0.5
            self._hotkeys.enabled = True
            self._on_message(f"「{job.name}」准备开始 · F8 暂停/继续 · F9 停止")
            self._thread = threading.Thread(
                target=self._run_loop,
                args=(job,),
                name="floatap-run",
                daemon=True,
            )
            self._thread.start()

    def pause(self) -> None:
        with self._lock:
            if self.phase in (RunPhase.RUNNING, RunPhase.ARMED):
                self._pause.clear()
                self._set_phase(RunPhase.PAUSED)
                self._on_message("已暂停 · F8 继续 · F9 停止")

    def resume(self) -> None:
        with self._lock:
            if self.phase == RunPhase.PAUSED:
                self._pause.set()
                self._set_phase(RunPhase.RUNNING)
                self._on_message("继续点击 · F8 暂停 · F9 停止")

    def stop(self, reason: StopReason = StopReason.USER) -> None:
        with self._lock:
            if self.phase == RunPhase.IDLE:
                return
            self._stop.set()
            self._pause.set()
            self._watcher.enabled = False
            self._hotkeys.enabled = False
            self._set_phase(RunPhase.IDLE)
            labels = {
                StopReason.USER: "已停止",
                StopReason.DURATION: "到达持续时长，已停止",
                StopReason.CLICK_LIMIT: "到达点击次数，已停止",
                StopReason.LOOP_LIMIT: "循环完成，已停止",
                StopReason.DISPLAY_CHANGED: "屏幕尺寸变化，已停止",
            }
            self._on_message(labels.get(reason, "已停止"))

    def _hotkey_pause_toggle(self) -> None:
        if self.phase == RunPhase.PAUSED:
            self.resume()
        elif self.phase in (RunPhase.RUNNING, RunPhase.ARMED):
            self.pause()

    def _hotkey_stop(self) -> None:
        if self.phase != RunPhase.IDLE:
            self.stop(StopReason.USER)

    def _user_touch(self) -> None:
        if self.phase in (RunPhase.RUNNING, RunPhase.ARMED):
            self.pause()

    def _set_phase(self, phase: RunPhase) -> None:
        self.phase = phase
        try:
            self._on_phase(phase)
        except Exception:
            pass

    def _poll_controls(self) -> None:
        self._watcher.poll()
        self._hotkeys.poll()

    def _wait_interruptible(self, total_ms: int) -> bool:
        remaining = total_ms
        saw_pause = False
        while remaining > 0:
            if self._stop.is_set():
                return False
            self._poll_controls()
            if not self._pause.is_set():
                saw_pause = True
                time.sleep(0.05)
                continue
            if saw_pause:
                saw_pause = False
                remaining = total_ms
            slice_ms = min(remaining, 50)
            time.sleep(slice_ms / 1000.0)
            if self._pause.is_set() and not self._stop.is_set():
                remaining -= slice_ms
        return not self._stop.is_set()

    def _run_loop(self, job: Job) -> None:
        try:
            seconds = max(0, job.countdown_sec)
            while seconds > 0:
                if self._stop.is_set():
                    return
                self._poll_controls()
                if self.phase == RunPhase.PAUSED or not self._pause.is_set():
                    time.sleep(0.05)
                    continue
                if self.phase != RunPhase.ARMED:
                    return
                self._on_message(f"「{job.name}」{seconds} 秒后开始 · F8 暂停 · F9 停止")
                if not self._wait_interruptible(1000):
                    return
                if self.phase == RunPhase.ARMED:
                    seconds -= 1

            while not self._pause.is_set():
                if self._stop.is_set():
                    return
                self._poll_controls()
                time.sleep(0.05)
            if self._stop.is_set():
                return
            self._set_phase(RunPhase.RUNNING)

            progress = Progress()
            index = 0
            rng = random.Random()
            step_count = len(job.steps)

            while not self._stop.is_set():
                while not self._pause.is_set():
                    if self._stop.is_set():
                        return
                    self._poll_controls()
                    time.sleep(0.05)
                if self._stop.is_set():
                    return
                if self.phase == RunPhase.PAUSED:
                    self._set_phase(RunPhase.RUNNING)

                current = job.steps[index]
                wait = wait_millis(current.wait_min_ms, current.wait_max_ms, rng)
                if not self._wait_interruptible(wait):
                    return
                if self.phase == RunPhase.PAUSED:
                    continue
                if self._stop.is_set() or self.phase != RunPhase.RUNNING:
                    break

                if not display_matches(current, read_display()):
                    self.stop(StopReason.DISPLAY_CHANGED)
                    return

                if progress.first_click_elapsed is None:
                    if job.click_limit is not None and progress.clicks >= job.click_limit:
                        self.stop(StopReason.CLICK_LIMIT)
                        return
                    if job.loop_limit is not None and progress.loops >= job.loop_limit:
                        self.stop(StopReason.LOOP_LIMIT)
                        return
                else:
                    elapsed = int(time.time() * 1000) - progress.first_click_elapsed
                    reason = next_stop(
                        job.duration_ms,
                        job.click_limit,
                        job.loop_limit,
                        Progress(progress.clicks, progress.loops, 0),
                        elapsed,
                    )
                    if reason is not None:
                        self.stop(reason)
                        return

                self._watcher.ignore_until = time.time() + 0.4
                try:
                    ok = click.tap(
                        current.x,
                        current.y,
                        restore_cursor=True,
                        stop_check=self._stop.is_set,
                    )
                except OSError as exc:
                    self._on_message(f"点击失败：{exc}")
                    self.stop(StopReason.USER)
                    return
                if not ok:
                    if self._stop.is_set():
                        return
                    # User was dragging; wait a bit and retry the same step.
                    self._on_message("检测到你在拖动鼠标，稍后再点 · F9 停止")
                    if not self._wait_interruptible(300):
                        return
                    continue

                if self._on_click:
                    try:
                        self._on_click(index, current.x, current.y)
                    except Exception:
                        pass

                first = progress.first_click_elapsed or int(time.time() * 1000)
                clicks = progress.clicks + 1
                clicked_step = index + 1
                next_index = 0 if index + 1 >= step_count else index + 1
                loops = progress.loops + 1 if next_index == 0 else progress.loops
                progress = Progress(clicks, loops, first)
                index = next_index
                self._on_message(
                    f"「{job.name}」第 {clicked_step} 步，已点击 {clicks} 次 · F9 停止"
                )

                if job.loop_limit is not None and loops >= job.loop_limit and next_index == 0:
                    self.stop(StopReason.LOOP_LIMIT)
                    return
                elapsed = int(time.time() * 1000) - first
                after = next_stop(
                    job.duration_ms,
                    job.click_limit,
                    job.loop_limit,
                    Progress(clicks, loops, 0),
                    elapsed,
                )
                if after in (StopReason.CLICK_LIMIT, StopReason.DURATION):
                    self.stop(after)
                    return
        finally:
            self._watcher.enabled = False
            self._hotkeys.enabled = False
            if self.phase != RunPhase.IDLE:
                self._set_phase(RunPhase.IDLE)
