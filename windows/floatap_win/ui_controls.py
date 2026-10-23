from __future__ import annotations

import tkinter as tk
from typing import Callable, Optional

from .models import RunPhase


class RunControlBar:
    """Always-on-top pause / resume / stop panel shown while a job is active."""

    def __init__(
        self,
        root: tk.Tk,
        on_pause: Callable[[], None],
        on_resume: Callable[[], None],
        on_stop: Callable[[], None],
    ) -> None:
        self.root = root
        self.on_pause = on_pause
        self.on_resume = on_resume
        self.on_stop = on_stop
        self.phase = RunPhase.IDLE
        self.win: Optional[tk.Toplevel] = None
        self._pause_btn: Optional[tk.Button] = None

    def update(self, phase: RunPhase) -> None:
        self.phase = phase
        if phase == RunPhase.IDLE:
            self.hide()
            return
        self.show()
        if self._pause_btn is not None:
            if phase == RunPhase.PAUSED:
                self._pause_btn.configure(text="继续", command=self.on_resume, bg="#0F766E")
            else:
                self._pause_btn.configure(text="暂停", command=self.on_pause, bg="#9A6700")

    def show(self) -> None:
        if self.win is not None and self.win.winfo_exists():
            self.win.deiconify()
            self.win.lift()
            return
        win = tk.Toplevel(self.root)
        win.title("浮序控制")
        win.attributes("-topmost", True)
        win.resizable(False, False)
        win.configure(bg="#1B242C")
        frame = tk.Frame(win, bg="#1B242C", padx=12, pady=12)
        frame.pack()
        tk.Label(
            frame,
            text="运行控制",
            fg="white",
            bg="#1B242C",
            font=("Segoe UI", 12, "bold"),
        ).pack(anchor="w", pady=(0, 8))
        row = tk.Frame(frame, bg="#1B242C")
        row.pack()
        self._pause_btn = tk.Button(
            row,
            text="暂停",
            width=10,
            height=2,
            font=("Segoe UI", 12, "bold"),
            bg="#9A6700",
            fg="white",
            relief="flat",
            command=self.on_pause,
        )
        self._pause_btn.pack(side="left", padx=6)
        tk.Button(
            row,
            text="停止",
            width=10,
            height=2,
            font=("Segoe UI", 12, "bold"),
            bg="#CF222E",
            fg="white",
            relief="flat",
            command=self.on_stop,
        ).pack(side="left", padx=6)
        tk.Label(
            frame,
            text="也可按 F8 暂停/继续，F9 停止",
            fg="#8B949E",
            bg="#1B242C",
            font=("Segoe UI", 9),
        ).pack(anchor="w", pady=(8, 0))
        # Place near top-right of primary screen.
        win.update_idletasks()
        w, h = win.winfo_reqwidth(), win.winfo_reqheight()
        sw = win.winfo_screenwidth()
        win.geometry(f"+{max(20, sw - w - 40)}+40")
        win.protocol("WM_DELETE_WINDOW", self.on_stop)
        self.win = win

    def hide(self) -> None:
        if self.win is not None:
            try:
                self.win.destroy()
            except tk.TclError:
                pass
            self.win = None
            self._pause_btn = None


class MainRunButtons:
    """Pause / stop buttons fixed on the main window."""

    def __init__(
        self,
        parent: tk.Widget,
        on_pause: Callable[[], None],
        on_resume: Callable[[], None],
        on_stop: Callable[[], None],
    ) -> None:
        self.on_pause = on_pause
        self.on_resume = on_resume
        self.on_stop = on_stop
        self.frame = tk.Frame(parent, bg="#F6F8FA", padx=12, pady=10)
        self.frame.pack(side="bottom", fill="x")
        tk.Label(
            self.frame,
            text="运行控制",
            font=("Segoe UI", 10, "bold"),
            bg="#F6F8FA",
        ).pack(side="left", padx=(0, 12))
        self.pause_btn = tk.Button(
            self.frame,
            text="暂停",
            width=10,
            font=("Segoe UI", 11, "bold"),
            bg="#9A6700",
            fg="white",
            relief="flat",
            state="disabled",
            command=self.on_pause,
        )
        self.pause_btn.pack(side="left", padx=4)
        self.stop_btn = tk.Button(
            self.frame,
            text="停止",
            width=10,
            font=("Segoe UI", 11, "bold"),
            bg="#CF222E",
            fg="white",
            relief="flat",
            state="disabled",
            command=self.on_stop,
        )
        self.stop_btn.pack(side="left", padx=4)
        tk.Label(
            self.frame,
            text="F8 暂停/继续 · F9 停止",
            fg="#656D76",
            bg="#F6F8FA",
            font=("Segoe UI", 9),
        ).pack(side="right")

    def update(self, phase: RunPhase) -> None:
        if phase == RunPhase.IDLE:
            self.pause_btn.configure(text="暂停", state="disabled", command=self.on_pause)
            self.stop_btn.configure(state="disabled")
            return
        self.stop_btn.configure(state="normal")
        if phase == RunPhase.PAUSED:
            self.pause_btn.configure(text="继续", state="normal", command=self.on_resume, bg="#0F766E")
        else:
            self.pause_btn.configure(text="暂停", state="normal", command=self.on_pause, bg="#9A6700")
