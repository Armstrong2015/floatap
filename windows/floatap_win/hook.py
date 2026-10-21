from __future__ import annotations

import ctypes
import threading
from ctypes import wintypes
from typing import Callable, Optional

from .click import is_own_extra

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

WH_MOUSE_LL = 14
WM_LBUTTONDOWN = 0x0201
HC_ACTION = 0


class MSLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [
        ("pt", wintypes.POINT),
        ("mouseData", wintypes.DWORD),
        ("flags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_size_t),
    ]


LowLevelMouseProc = ctypes.WINFUNCTYPE(
    ctypes.c_long,
    ctypes.c_int,
    wintypes.WPARAM,
    wintypes.LPARAM,
)


class MousePauseHook:
    """Pause callback when the user presses the left button (not our SendInput)."""

    def __init__(self, on_user_down: Callable[[], None]) -> None:
        self._on_user_down = on_user_down
        self._hook = None
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._proc = LowLevelMouseProc(self._callback)
        self.enabled = True

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="floatap-mouse-hook", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._hook:
            user32.UnhookWindowsHookEx(self._hook)
            self._hook = None
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.0)

    def _run(self) -> None:
        self._hook = user32.SetWindowsHookExW(WH_MOUSE_LL, self._proc, None, 0)
        if not self._hook:
            return
        msg = wintypes.MSG()
        while not self._stop.is_set():
            result = user32.GetMessageW(ctypes.byref(msg), None, 0, 0)
            if result == 0 or result == -1:
                break
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))
        if self._hook:
            user32.UnhookWindowsHookEx(self._hook)
            self._hook = None

    def _callback(self, n_code: int, w_param: int, l_param: int) -> int:
        if n_code == HC_ACTION and w_param == WM_LBUTTONDOWN and self.enabled:
            info = ctypes.cast(l_param, ctypes.POINTER(MSLLHOOKSTRUCT)).contents
            if not is_own_extra(int(info.dwExtraInfo)):
                try:
                    self._on_user_down()
                except Exception:
                    pass
        return user32.CallNextHookEx(None, n_code, w_param, l_param)
