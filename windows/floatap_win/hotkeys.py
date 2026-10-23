from __future__ import annotations

import ctypes
from typing import Callable, Optional

user32 = ctypes.windll.user32

VK_F8 = 0x77
VK_F9 = 0x78


class HotkeyWatcher:
    """Poll F8 (pause/resume) and F9 (stop) without installing a keyboard hook."""

    def __init__(
        self,
        on_f8: Callable[[], None],
        on_f9: Callable[[], None],
    ) -> None:
        self._on_f8 = on_f8
        self._on_f9 = on_f9
        self.enabled = False
        self._f8_down = False
        self._f9_down = False

    def poll(self) -> None:
        if not self.enabled:
            self._f8_down = self._down(VK_F8)
            self._f9_down = self._down(VK_F9)
            return
        f8 = self._down(VK_F8)
        f9 = self._down(VK_F9)
        if f8 and not self._f8_down:
            try:
                self._on_f8()
            except Exception:
                pass
        if f9 and not self._f9_down:
            try:
                self._on_f9()
            except Exception:
                pass
        self._f8_down = f8
        self._f9_down = f9

    @staticmethod
    def _down(vk: int) -> bool:
        return bool(user32.GetAsyncKeyState(vk) & 0x8000)
