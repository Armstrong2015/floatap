from __future__ import annotations

import ctypes

from .models import DisplaySnapshot

user32 = ctypes.windll.user32


def enable_dpi_awareness() -> None:
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)  # PROCESS_PER_MONITOR_DPI_AWARE_V2
        return
    except Exception:
        pass
    try:
        user32.SetProcessDPIAware()
    except Exception:
        pass


def read_display() -> DisplaySnapshot:
    width = int(user32.GetSystemMetrics(0))
    height = int(user32.GetSystemMetrics(1))
    return DisplaySnapshot(width=width, height=height, rotation=0)


def virtual_screen() -> tuple[int, int, int, int]:
    """left, top, width, height of the virtual screen spanning all monitors."""
    left = int(user32.GetSystemMetrics(76))  # SM_XVIRTUALSCREEN
    top = int(user32.GetSystemMetrics(77))  # SM_YVIRTUALSCREEN
    width = int(user32.GetSystemMetrics(78))  # SM_CXVIRTUALSCREEN
    height = int(user32.GetSystemMetrics(79))  # SM_CYVIRTUALSCREEN
    if width <= 0 or height <= 0:
        snap = read_display()
        return 0, 0, snap.width, snap.height
    return left, top, width, height
