from __future__ import annotations

import tkinter as tk
from typing import Callable, Optional

from .models import JobSummary, RunPhase


COLORS = {
    RunPhase.IDLE: "#0F766E",
    RunPhase.ARMED: "#9A6700",
    RunPhase.RUNNING: "#1F6FEB",
    RunPhase.PAUSED: "#6E7781",
}


class FloatingBall:
    def __init__(
        self,
        root: tk.Tk,
        on_prepare_idle: Callable[[], None],
        on_start: Callable[[], None],
        on_pause: Callable[[], None],
        on_resume: Callable[[], None],
        on_stop: Callable[[], None],
        on_pick: Callable[[], None],
        on_open: Callable[[], None],
        on_select_job: Callable[[str, str], None],
        on_move: Callable[[int, int], None],
    ) -> None:
        self.root = root
        self.on_prepare_idle = on_prepare_idle
        self.on_start = on_start
        self.on_pause = on_pause
        self.on_resume = on_resume
        self.on_stop = on_stop
        self.on_pick = on_pick
        self.on_open = on_open
        self.on_select_job = on_select_job
        self.on_move = on_move
        self.phase = RunPhase.IDLE
        self.win = tk.Toplevel(root)
        self.win.overrideredirect(True)
        self.win.attributes("-topmost", True)
        self.size = 56
        self.win.geometry(f"{self.size}x{self.size}+24+240")
        self.canvas = tk.Canvas(
            self.win,
            width=self.size,
            height=self.size,
            highlightthickness=0,
            bg="#0F766E",
        )
        self.canvas.pack(fill="both", expand=True)
        self._oval = self.canvas.create_oval(2, 2, self.size - 2, self.size - 2, fill="#0F766E", outline="")
        self.canvas.bind("<ButtonPress-1>", self._down)
        self.canvas.bind("<B1-Motion>", self._drag)
        self.canvas.bind("<ButtonRelease-1>", self._up)
        self._drag_ox = 0
        self._drag_oy = 0
        self._dragging = False
        self._closed_menu_on_down = False
        self.menu: Optional[tk.Toplevel] = None
        self.paint(RunPhase.IDLE)

    def set_position(self, x: int, y: int) -> None:
        self.win.geometry(f"{self.size}x{self.size}+{x}+{y}")

    def position(self) -> tuple[int, int]:
        return self.win.winfo_x(), self.win.winfo_y()

    def paint(self, phase: RunPhase) -> None:
        self.phase = phase
        color = COLORS.get(phase, "#0F766E")
        self.canvas.configure(bg=color)
        self.canvas.itemconfigure(self._oval, fill=color)

    def hide_menu(self) -> None:
        if self.menu is not None:
            try:
                self.menu.destroy()
            except tk.TclError:
                pass
            self.menu = None

    def show_idle_menu(self, jobs: list[JobSummary], selected_id: str) -> None:
        self.hide_menu()
        menu = tk.Toplevel(self.win)
        menu.overrideredirect(True)
        menu.attributes("-topmost", True)
        menu.configure(bg="#1B242C")
        frame = tk.Frame(menu, bg="#1B242C", padx=8, pady=8)
        frame.pack()
        if not jobs:
            tk.Label(frame, text="还没有作业", fg="#D0D7DE", bg="#1B242C").pack(anchor="w", pady=4)
        else:
            for job in jobs[:12]:
                label = f"当前 {job.name}" if job.id == selected_id else job.name
                self._btn(frame, label, lambda jid=job.id, name=job.name: self._pick_job(jid, name))
        self._btn(frame, "选点", self._do_pick)
        self._btn(frame, "开始", self._do_start)
        self._btn(frame, "打开", self._do_open)
        bx, by = self.position()
        menu.update_idletasks()
        menu.geometry(f"+{bx + self.size + 8}+{by}")
        self.menu = menu

    def show_action_menu(self, primary: str, primary_action: Callable[[], None]) -> None:
        self.hide_menu()
        menu = tk.Toplevel(self.win)
        menu.overrideredirect(True)
        menu.attributes("-topmost", True)
        menu.configure(bg="#1B242C")
        frame = tk.Frame(menu, bg="#1B242C", padx=8, pady=8)
        frame.pack()
        self._btn(frame, primary, lambda: (self.hide_menu(), primary_action()))
        self._btn(frame, "停止", lambda: (self.hide_menu(), self.on_stop()))
        bx, by = self.position()
        menu.update_idletasks()
        menu.geometry(f"+{bx + self.size + 8}+{by}")
        self.menu = menu

    def _btn(self, parent: tk.Widget, text: str, command: Callable[[], None]) -> None:
        tk.Button(
            parent,
            text=text,
            command=command,
            bg="#33FFFFFF",
            fg="white",
            relief="flat",
            font=("Segoe UI", 11),
            padx=12,
            pady=6,
            anchor="w",
            width=16,
        ).pack(fill="x", pady=2)

    def _pick_job(self, job_id: str, name: str) -> None:
        self.hide_menu()
        self.on_select_job(job_id, name)

    def _do_pick(self) -> None:
        self.hide_menu()
        self.on_pick()

    def _do_start(self) -> None:
        self.hide_menu()
        self.on_start()

    def _do_open(self) -> None:
        self.hide_menu()
        self.on_open()

    def _down(self, event: tk.Event) -> None:
        self._dragging = False
        self._closed_menu_on_down = self.menu is not None
        if self._closed_menu_on_down:
            self.hide_menu()
        self._drag_ox = event.x_root - self.win.winfo_x()
        self._drag_oy = event.y_root - self.win.winfo_y()

    def _drag(self, event: tk.Event) -> None:
        dx = event.x_root - (self.win.winfo_x() + self._drag_ox)
        dy = event.y_root - (self.win.winfo_y() + self._drag_oy)
        if abs(dx) > 4 or abs(dy) > 4 or self._dragging:
            self._dragging = True
            x = event.x_root - self._drag_ox
            y = event.y_root - self._drag_oy
            self.win.geometry(f"{self.size}x{self.size}+{x}+{y}")

    def _up(self, event: tk.Event) -> None:
        if self._dragging:
            x, y = self.position()
            self.on_move(x, y)
            self._dragging = False
            return
        if self._closed_menu_on_down:
            self._closed_menu_on_down = False
            return
        if self.phase in (RunPhase.RUNNING, RunPhase.ARMED):
            self.show_action_menu("暂停", self.on_pause)
        elif self.phase == RunPhase.PAUSED:
            self.show_action_menu("继续", self.on_resume)
        else:
            self.on_prepare_idle()
