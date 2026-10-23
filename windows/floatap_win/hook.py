"""User-input pause detection without a global low-level mouse hook.

WH_MOUSE_LL + Python callbacks can freeze the whole system mouse if the
callback is slow or the process hangs. Poll left-button state instead.
"""

from __future__ import annotations

import ctypes
import time
from typing import Callable

user32 = ctypes.windll.user32
VK_LBUTTON = 0x01


class MousePauseWatcher:
    """Detect a fresh left-button press by polling GetAsyncKeyState."""

    def __init__(self, on_user_down: Callable[[], None]) -> None:
        self._on_user_down = on_user_down
        self.enabled = False
        self._was_down = False
        self.ignore_until = 0.0

    def start(self) -> None:
        self._was_down = self._left_down()

    def stop(self) -> None:
        self.enabled = False
        self._was_down = False

    def poll(self) -> None:
        if not self.enabled:
            self._was_down = self._left_down()
            return
        down = self._left_down()
        if down and not self._was_down and time.time() >= self.ignore_until:
            try:
                self._on_user_down()
            except Exception:
                pass
        self._was_down = down

    @staticmethod
    def _left_down() -> bool:
        # High bit set means the key is currently down.
        return bool(user32.GetAsyncKeyState(VK_LBUTTON) & 0x8000)
