from __future__ import annotations

import ctypes
from ctypes import wintypes

user32 = ctypes.windll.user32

INPUT_MOUSE = 0
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_ABSOLUTE = 0x8000
MOUSEEVENTF_VIRTUALDESK = 0x4000


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD),
    ]


class INPUT_UNION(ctypes.Union):
    _fields_ = [
        ("mi", MOUSEINPUT),
        ("ki", KEYBDINPUT),
        ("hi", HARDWAREINPUT),
    ]


class INPUT(ctypes.Structure):
    _fields_ = [
        ("type", wintypes.DWORD),
        ("union", INPUT_UNION),
    ]


# Extra info marker so our own injected clicks can be ignored by the pause hook.
OWN_CLICK_EXTRA = 0xF107A70


def _abs_coords(x: int, y: int) -> tuple[int, int]:
    left = int(user32.GetSystemMetrics(76))
    top = int(user32.GetSystemMetrics(77))
    width = int(user32.GetSystemMetrics(78)) or int(user32.GetSystemMetrics(0))
    height = int(user32.GetSystemMetrics(79)) or int(user32.GetSystemMetrics(1))
    # Map to 0..65535 across the virtual desktop.
    abs_x = int(round((x - left) * 65535 / max(width - 1, 1)))
    abs_y = int(round((y - top) * 65535 / max(height - 1, 1)))
    return abs_x, abs_y


def _send(flags: int, x: int = 0, y: int = 0) -> None:
    extra = ctypes.c_ulong(OWN_CLICK_EXTRA)
    inp = INPUT()
    inp.type = INPUT_MOUSE
    inp.union.mi = MOUSEINPUT(x, y, 0, flags, 0, ctypes.pointer(extra))
    sent = user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
    if sent != 1:
        raise OSError(f"SendInput failed: {ctypes.GetLastError()}")


def tap(x: int, y: int) -> None:
    abs_x, abs_y = _abs_coords(x, y)
    flags_move = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK
    _send(flags_move, abs_x, abs_y)
    _send(MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK, abs_x, abs_y)
    _send(MOUSEEVENTF_LEFTUP | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK, abs_x, abs_y)


def is_own_extra(extra_info: int) -> bool:
    return (extra_info & 0xFFFFFFFF) == OWN_CLICK_EXTRA
