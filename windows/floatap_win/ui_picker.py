from __future__ import annotations

import tkinter as tk
from typing import Callable, Optional

from .display import virtual_screen


class PickerOverlay:
    def __init__(
        self,
        root: tk.Tk,
        on_pick: Callable[[int, int], None],
        on_cancel: Callable[[], None],
    ) -> None:
        self.root = root
        self.on_pick = on_pick
        self.on_cancel = on_cancel
        self.win: Optional[tk.Toplevel] = None

    def show(self) -> None:
        self.hide()
        left, top, width, height = virtual_screen()
        win = tk.Toplevel(self.root)
        win.overrideredirect(True)
        win.attributes("-topmost", True)
        try:
            win.attributes("-alpha", 0.25)
        except tk.TclError:
            pass
        win.configure(bg="#0F766E")
        win.geometry(f"{width}x{height}+{left}+{top}")
        win.bind("<Button-1>", self._clicked)
        label = tk.Label(
            win,
            text="点按要记录的位置",
            fg="white",
            bg="#0F766E",
            font=("Segoe UI", 18),
        )
        label.place(relx=0.5, rely=0.08, anchor="center")
        cancel = tk.Button(
            win,
            text="取消选点",
            command=self._cancel,
            font=("Segoe UI", 12),
            bg="#1B242C",
            fg="white",
            relief="flat",
            padx=16,
            pady=8,
        )
        cancel.place(x=24, y=24)
        self.win = win
        win.focus_force()

    def hide(self) -> None:
        if self.win is not None:
            try:
                self.win.destroy()
            except tk.TclError:
                pass
            self.win = None

    def _clicked(self, event: tk.Event) -> None:
        if self.win is None:
            return
        # event.x_root / y_root are screen coordinates
        x, y = int(event.x_root), int(event.y_root)
        self.hide()
        self.on_pick(x, y)

    def _cancel(self) -> None:
        self.hide()
        self.on_cancel()
