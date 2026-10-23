from __future__ import annotations

import tkinter as tk
from typing import Optional


class ClickFlash:
    """Brief ring at the programmatic click point (does not steal focus)."""

    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self._wins: list[tk.Toplevel] = []

    def show(self, x: int, y: int) -> None:
        self.root.after(0, lambda: self._draw(x, y))

    def _draw(self, x: int, y: int) -> None:
        size = 48
        win = tk.Toplevel(self.root)
        win.overrideredirect(True)
        win.attributes("-topmost", True)
        try:
            win.attributes("-transparentcolor", "#010101")
        except tk.TclError:
            pass
        try:
            win.attributes("-alpha", 0.92)
        except tk.TclError:
            pass
        win.geometry(f"{size}x{size}+{x - size // 2}+{y - size // 2}")
        # Ignore mouse so the flash never blocks the user.
        try:
            win.attributes("-disabled", True)
        except tk.TclError:
            pass
        canvas = tk.Canvas(
            win,
            width=size,
            height=size,
            bg="#010101",
            highlightthickness=0,
            bd=0,
        )
        canvas.pack(fill="both", expand=True)
        canvas.create_oval(4, 4, size - 4, size - 4, outline="#FFC14D", width=3)
        canvas.create_oval(14, 14, size - 14, size - 14, outline="#FFB020", width=2)
        self._wins.append(win)

        def close() -> None:
            try:
                win.destroy()
            except tk.TclError:
                pass
            if win in self._wins:
                self._wins.remove(win)

        win.after(380, close)
