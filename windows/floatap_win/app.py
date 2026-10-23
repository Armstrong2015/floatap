from __future__ import annotations

import tkinter as tk
from tkinter import messagebox, ttk
from typing import Optional

from .display import enable_dpi_awareness, read_display
from .engine import RunEngine
from .models import Job, Prefs, RunPhase, Step, WaitMode
from .rules import JobDraft, StepDraft, check_draft
from .store import JobStore
from .ui_ball import FloatingBall
from .ui_flash import ClickFlash
from .ui_picker import PickerOverlay


class FloatapApp:
    def __init__(self) -> None:
        enable_dpi_awareness()
        self.store = JobStore()
        self.prefs = self.store.prefs()
        self.root = tk.Tk()
        self.root.title("浮序 Floatap")
        self.root.geometry("720x780")
        self.root.minsize(560, 640)
        self.status = tk.StringVar(value="准备就绪 · 运行中按 F8 暂停，F9 停止")
        self._edit_job_id = ""
        self._steps: list[dict] = []
        self._picking_index = 0
        self.flash = ClickFlash(self.root)
        self.engine = RunEngine(
            on_phase=self._on_phase,
            on_message=self._on_message,
            on_click=self._on_program_click,
        )
        self.picker = PickerOverlay(self.root, self._picked, self._pick_cancelled)
        self.ball = FloatingBall(
            self.root,
            on_prepare_idle=self._ball_idle_menu,
            on_start=self._ball_start,
            on_pause=self.engine.pause,
            on_resume=self.engine.resume,
            on_stop=lambda: self.engine.stop(),
            on_pick=self._ball_pick,
            on_open=self._ball_open,
            on_select_job=self._ball_select_job,
            on_move=self._ball_moved,
        )
        self.ball.set_position(self.prefs.ball_x, self.prefs.ball_y)
        self.container = ttk.Frame(self.root, padding=16)
        self.container.pack(fill="both", expand=True)
        self.root.protocol("WM_DELETE_WINDOW", self._quit)
        if not self.prefs.boundary_accepted:
            self._show_guide()
        else:
            self._show_home()

    def run(self) -> None:
        self.root.mainloop()

    def _quit(self) -> None:
        self.engine.shutdown()
        self.prefs.ball_x, self.prefs.ball_y = self.ball.position()
        self.store.save_prefs(self.prefs)
        self.root.destroy()

    def _clear(self) -> None:
        for child in self.container.winfo_children():
            child.destroy()

    def _on_phase(self, phase: RunPhase) -> None:
        self.root.after(0, lambda: self.ball.paint(phase))

    def _on_message(self, text: str) -> None:
        self.root.after(0, lambda: self.status.set(text))

    def _on_program_click(self, _step: int, x: int, y: int) -> None:
        self.flash.show(x, y)

    def _show_guide(self) -> None:
        self._clear()
        ttk.Label(self.container, text="浮序", font=("Segoe UI", 28, "bold")).pack(anchor="w")
        ttk.Label(
            self.container,
            text="在自己的电脑上，选定屏幕坐标，按间隔与顺序把点击做完。",
            wraplength=640,
            font=("Segoe UI", 12),
        ).pack(anchor="w", pady=(8, 16))
        body = (
            "浮序只点击你亲手标出的位置。\n"
            "请遵守目标软件与平台的规则，以及当地法律。\n"
            "不做注入、不绕过权限、不对抗反作弊或验证码。\n\n"
            "运行中：F8 暂停/继续，F9 立即停止。\n"
            "程序点击后会把光标还回原处，并在落点闪一下圆圈。"
        )
        ttk.Label(self.container, text=body, justify="left", font=("Segoe UI", 11)).pack(anchor="w")
        ttk.Button(self.container, text="我知道了", command=self._accept_guide).pack(anchor="w", pady=24)

    def _accept_guide(self) -> None:
        self.prefs.boundary_accepted = True
        self.store.save_prefs(self.prefs)
        self._show_home()

    def _show_home(self) -> None:
        self._clear()
        head = ttk.Frame(self.container)
        head.pack(fill="x")
        ttk.Label(head, text="作业", font=("Segoe UI", 22, "bold")).pack(side="left")
        ttk.Button(head, text="新建作业", command=self._new_job).pack(side="right")
        ttk.Label(self.container, textvariable=self.status, foreground="#555").pack(anchor="w", pady=(8, 4))
        ttk.Label(
            self.container,
            text="运行中请用键盘：F8 暂停/继续，F9 停止（不必去抢悬浮球）。",
            foreground="#0F766E",
        ).pack(anchor="w", pady=(0, 12))
        jobs = self.store.summaries()
        if not jobs:
            ttk.Label(self.container, text="还没有作业。点右上角新建一份。").pack(anchor="w")
            return
        for job in jobs:
            row = ttk.Frame(self.container)
            row.pack(fill="x", pady=4)
            ttk.Label(row, text=f"{job.name}（{job.step_count} 步）", font=("Segoe UI", 12)).pack(
                side="left"
            )
            ttk.Button(row, text="编辑", command=lambda jid=job.id: self._edit_job(jid)).pack(side="right", padx=4)
            ttk.Button(row, text="开始", command=lambda jid=job.id: self._start_job(jid)).pack(side="right")

    def _new_job(self) -> None:
        job = self.store.new_job()
        self._open_editor(job, is_new=True)

    def _edit_job(self, job_id: str) -> None:
        job = self.store.get(job_id)
        if job is None:
            messagebox.showerror("浮序", "这份作业已经不在了")
            self._show_home()
            return
        self._open_editor(job, is_new=False)

    def _open_editor(self, job: Job, is_new: bool) -> None:
        self._clear()
        self._edit_job_id = job.id
        self._is_new = is_new
        self.name_var = tk.StringVar(value=job.name)
        self.loop_var = tk.StringVar(value="" if job.loop_limit is None else str(job.loop_limit))
        self.duration_var = tk.StringVar(
            value="" if job.duration_ms is None else str(max(1, job.duration_ms // 1000))
        )
        self.clicks_var = tk.StringVar(value="" if job.click_limit is None else str(job.click_limit))
        self.countdown_var = tk.IntVar(value=job.countdown_sec)
        self.pause_var = tk.BooleanVar(value=job.pause_on_user_touch)
        self._steps = [
            {
                "name": step.name,
                "x": step.x,
                "y": step.y,
                "display_width": step.display_width,
                "display_height": step.display_height,
                "rotation": step.rotation,
                "interval": str(step.wait_min_ms),
                "random": step.wait_mode == WaitMode.RANGE or step.wait_max_ms > step.wait_min_ms,
                "interval_max": ""
                if step.wait_max_ms <= step.wait_min_ms
                else str(step.wait_max_ms),
            }
            for step in job.steps
        ]
        bar = ttk.Frame(self.container)
        bar.pack(fill="x")
        ttk.Button(bar, text="返回", command=self._show_home).pack(side="left")
        ttk.Label(bar, text="编辑作业" if not is_new else "新建作业", font=("Segoe UI", 16, "bold")).pack(
            side="left", padx=12
        )
        ttk.Button(bar, text="保存", command=self._save_editor).pack(side="right")

        form = ttk.Frame(self.container)
        form.pack(fill="both", expand=True, pady=12)
        canvas = tk.Canvas(form, highlightthickness=0)
        scroll = ttk.Scrollbar(form, orient="vertical", command=canvas.yview)
        self.editor_body = ttk.Frame(canvas)
        self.editor_body.bind(
            "<Configure>",
            lambda e: canvas.configure(scrollregion=canvas.bbox("all")),
        )
        canvas.create_window((0, 0), window=self.editor_body, anchor="nw")
        canvas.configure(yscrollcommand=scroll.set)
        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        ttk.Label(self.editor_body, text="名称").pack(anchor="w")
        ttk.Entry(self.editor_body, textvariable=self.name_var).pack(fill="x", pady=(0, 8))
        ttk.Label(
            self.editor_body,
            text="按顺序点击下面这些步骤，一轮结束后从头再来。",
        ).pack(anchor="w", pady=(0, 8))
        self.steps_host = ttk.Frame(self.editor_body)
        self.steps_host.pack(fill="x")
        self._render_steps()
        ttk.Button(self.editor_body, text="添加一步", command=self._add_step).pack(fill="x", pady=8)
        ttk.Label(self.editor_body, text="循环次数，留空表示不限").pack(anchor="w")
        ttk.Entry(self.editor_body, textvariable=self.loop_var).pack(fill="x", pady=(0, 8))
        ttk.Label(self.editor_body, text="持续时长（秒）").pack(anchor="w")
        ttk.Entry(self.editor_body, textvariable=self.duration_var).pack(fill="x", pady=(0, 8))
        ttk.Label(self.editor_body, text="点击次数，可留空").pack(anchor="w")
        ttk.Entry(self.editor_body, textvariable=self.clicks_var).pack(fill="x", pady=(0, 8))
        ttk.Label(self.editor_body, text="开始倒计时（秒）").pack(anchor="w")
        ttk.Scale(
            self.editor_body,
            from_=0,
            to=10,
            orient="horizontal",
            variable=self.countdown_var,
        ).pack(fill="x", pady=(0, 8))
        ttk.Checkbutton(
            self.editor_body,
            text="运行中用户按下鼠标左键则暂停",
            variable=self.pause_var,
        ).pack(anchor="w", pady=8)
        ttk.Label(self.editor_body, textvariable=self.status, foreground="#555").pack(anchor="w")

    def _render_steps(self) -> None:
        for child in self.steps_host.winfo_children():
            child.destroy()
        for index, step in enumerate(self._steps):
            card = ttk.LabelFrame(self.steps_host, text=f"第 {index + 1} 步", padding=8)
            card.pack(fill="x", pady=6)
            name = tk.StringVar(value=step["name"])
            interval = tk.StringVar(value=step["interval"])
            interval_max = tk.StringVar(value=step["interval_max"])
            random_wait = tk.BooleanVar(value=bool(step["random"]))

            def bind_name(var=name, i=index):
                step_ref = self._steps[i]
                step_ref["name"] = var.get()

            def bind_interval(var=interval, i=index):
                self._steps[i]["interval"] = "".join(ch for ch in var.get() if ch.isdigit())

            def bind_max(var=interval_max, i=index):
                self._steps[i]["interval_max"] = "".join(ch for ch in var.get() if ch.isdigit())

            def bind_random(var=random_wait, i=index, max_var=interval_max, max_row_holder=[]):
                self._steps[i]["random"] = bool(var.get())
                self._render_steps()

            name.trace_add("write", lambda *_a, v=name, i=index: self._steps.__setitem__(
                i, {**self._steps[i], "name": v.get()}
            ))
            ttk.Label(card, text="步骤名称").pack(anchor="w")
            ttk.Entry(card, textvariable=name).pack(fill="x")
            point = (
                "还没有选点"
                if step["x"] < 0
                else f"坐标 {step['x']}, {step['y']} · {step['display_width']}×{step['display_height']}"
            )
            ttk.Label(card, text=point).pack(anchor="w", pady=4)
            ttk.Button(card, text="选点", command=lambda i=index: self._begin_pick(i)).pack(anchor="w")
            ttk.Label(card, text="点击前等待（毫秒）").pack(anchor="w", pady=(8, 0))
            ttk.Entry(card, textvariable=interval).pack(fill="x")
            interval.trace_add(
                "write",
                lambda *_a, v=interval, i=index: self._steps.__setitem__(
                    i,
                    {
                        **self._steps[i],
                        "interval": "".join(ch for ch in v.get() if ch.isdigit()),
                    },
                ),
            )
            row = ttk.Frame(card)
            row.pack(fill="x", pady=4)
            ttk.Label(row, text="随机等待").pack(side="left")
            ttk.Checkbutton(
                row,
                variable=random_wait,
                command=lambda i=index, v=random_wait: self._toggle_random(i, v.get()),
            ).pack(side="right")
            if step["random"]:
                ttk.Label(card, text="最大点击前等待（毫秒）").pack(anchor="w")
                ttk.Entry(card, textvariable=interval_max).pack(fill="x")
                interval_max.trace_add(
                    "write",
                    lambda *_a, v=interval_max, i=index: self._steps.__setitem__(
                        i,
                        {
                            **self._steps[i],
                            "interval_max": "".join(ch for ch in v.get() if ch.isdigit()),
                        },
                    ),
                )
            actions = ttk.Frame(card)
            actions.pack(fill="x", pady=4)
            ttk.Button(
                actions,
                text="上移",
                command=lambda i=index: self._move_step(i, -1),
                state=("disabled" if index == 0 else "normal"),
            ).pack(side="left", padx=2)
            ttk.Button(
                actions,
                text="下移",
                command=lambda i=index: self._move_step(i, 1),
                state=("disabled" if index >= len(self._steps) - 1 else "normal"),
            ).pack(side="left", padx=2)
            ttk.Button(
                actions,
                text="删除",
                command=lambda i=index: self._delete_step(i),
                state=("disabled" if len(self._steps) <= 1 else "normal"),
            ).pack(side="left", padx=2)

    def _toggle_random(self, index: int, enabled: bool) -> None:
        self._steps[index]["random"] = enabled
        self._render_steps()

    def _add_step(self) -> None:
        self._steps.append(
            {
                "name": "点击",
                "x": -1,
                "y": -1,
                "display_width": 0,
                "display_height": 0,
                "rotation": 0,
                "interval": "500",
                "random": False,
                "interval_max": "",
            }
        )
        self._render_steps()

    def _move_step(self, index: int, delta: int) -> None:
        target = index + delta
        if target < 0 or target >= len(self._steps):
            return
        self._steps[index], self._steps[target] = self._steps[target], self._steps[index]
        self._render_steps()

    def _delete_step(self, index: int) -> None:
        if len(self._steps) <= 1:
            return
        del self._steps[index]
        self._render_steps()

    def _parse_optional_int(self, text: str) -> Optional[int]:
        text = text.strip()
        if not text:
            return None
        return int(text)

    def _save_editor(self, allow_missing: bool = False, then=None) -> bool:
        drafts = []
        for step in self._steps:
            interval = int(step["interval"] or -1)
            max_ms = None
            if step["random"]:
                max_ms = int(step["interval_max"] or -1)
            drafts.append(
                StepDraft(
                    name=step["name"],
                    x=int(step["x"]),
                    y=int(step["y"]),
                    display_width=int(step["display_width"]),
                    display_height=int(step["display_height"]),
                    rotation=int(step["rotation"]),
                    interval_ms=interval,
                    interval_max_ms=max_ms,
                )
            )
        try:
            loop = self._parse_optional_int(self.loop_var.get())
            duration = self._parse_optional_int(self.duration_var.get())
            clicks = self._parse_optional_int(self.clicks_var.get())
        except ValueError:
            self.status.set("数字填写有误")
            return False
        checked = check_draft(
            JobDraft(
                name=self.name_var.get().strip(),
                duration_sec=duration,
                click_limit=clicks,
                countdown_sec=int(self.countdown_var.get()),
                pause_on_user_touch=bool(self.pause_var.get()),
                existing_count=self.store.count(),
                is_new=self._is_new and not self._edit_job_id,
                steps=drafts,
                loop_limit=loop,
                allow_missing_point=allow_missing,
            )
        )
        if checked.issue is not None:
            self.status.set(checked.message() or "无法保存")
            return False
        steps = []
        for i, (ui, timing) in enumerate(zip(self._steps, checked.steps)):
            steps.append(
                Step(
                    order=i,
                    name=timing.name,
                    x=int(ui["x"]),
                    y=int(ui["y"]),
                    display_width=int(ui["display_width"]),
                    display_height=int(ui["display_height"]),
                    rotation=int(ui["rotation"]),
                    wait_mode=WaitMode.RANGE
                    if timing.interval_max_ms > timing.interval_ms
                    else WaitMode.FIXED,
                    wait_min_ms=timing.interval_ms,
                    wait_max_ms=timing.interval_max_ms,
                )
            )
            ui["interval"] = str(timing.interval_ms)
            if ui["random"]:
                ui["interval_max"] = str(timing.interval_max_ms)
        job = Job(
            id=self._edit_job_id,
            name=self.name_var.get().strip(),
            steps=steps,
            duration_ms=checked.duration_ms,
            click_limit=clicks if clicks and clicks > 0 else None,
            loop_limit=checked.loop_limit,
            countdown_sec=int(self.countdown_var.get()),
            pause_on_user_touch=bool(self.pause_var.get()),
        )
        job_id = self.store.save(job)
        self._edit_job_id = job_id
        self._is_new = False
        self.prefs.last_job_id = job_id
        self.store.save_prefs(self.prefs)
        if checked.interval_adjusted:
            self.status.set("已把过小的间隔调整为 50 毫秒")
        else:
            self.status.set("已保存")
        if then:
            then(job_id)
        return True

    def _begin_pick(self, index: int) -> None:
        self._picking_index = index
        self.prefs.last_step_index = index
        self.store.save_prefs(self.prefs)
        if not self._save_editor(allow_missing=True):
            return
        self.status.set(f"去选第 {index + 1} 步。点一下位置后会回到本页。")
        self.root.iconify()
        self.root.after(200, self.picker.show)

    def _picked(self, x: int, y: int) -> None:
        display = read_display()
        index = self.prefs.last_step_index
        job = self.store.get(self.prefs.last_job_id or self._edit_job_id)
        if job is None:
            job = self.store.new_job()
            job.steps[0].x = x
            job.steps[0].y = y
            job.steps[0].display_width = display.width
            job.steps[0].display_height = display.height
            job_id = self.store.save(job)
            self.prefs.last_job_id = job_id
            self.store.save_prefs(self.prefs)
            self.status.set(f"已新建并记下 {x}, {y}")
            self.root.deiconify()
            self.root.lift()
            self._edit_job(job_id)
            return
        index = max(0, min(index, len(job.steps) - 1))
        steps = list(job.steps)
        steps[index] = Step(
            order=index,
            name=steps[index].name,
            x=x,
            y=y,
            display_width=display.width,
            display_height=display.height,
            rotation=0,
            wait_mode=steps[index].wait_mode,
            wait_min_ms=steps[index].wait_min_ms,
            wait_max_ms=steps[index].wait_max_ms,
        )
        job.steps = steps
        self.store.save(job)
        self.status.set(f"已记下「{job.name}」第 {index + 1} 步 {x}, {y}")
        self.root.deiconify()
        self.root.lift()
        self._edit_job(job.id)

    def _pick_cancelled(self) -> None:
        self.root.deiconify()
        self.root.lift()
        job_id = self.prefs.last_job_id or self._edit_job_id
        if job_id:
            self._edit_job(job_id)
        else:
            self._show_home()
        self.status.set("已取消选点")

    def _start_job(self, job_id: str) -> None:
        job = self.store.get(job_id)
        if job is None:
            self.status.set("这份作业已经不在了")
            return
        self.prefs.last_job_id = job_id
        self.store.save_prefs(self.prefs)
        self.engine.start(job)

    def _ball_idle_menu(self) -> None:
        self.ball.show_idle_menu(self.store.summaries(), self.prefs.last_job_id)

    def _ball_start(self) -> None:
        job_id = self.prefs.last_job_id
        if not job_id:
            jobs = self.store.summaries()
            if not jobs:
                self.status.set("先新建一份作业")
                return
            job_id = jobs[0].id
            self.prefs.last_job_id = job_id
            self.store.save_prefs(self.prefs)
        self._start_job(job_id)

    def _ball_pick(self) -> None:
        job_id = self.prefs.last_job_id
        if not job_id:
            self.status.set("先选择一份作业")
            return
        job = self.store.get(job_id)
        if job is None:
            self.status.set("这份作业已经不在了")
            return
        self._edit_job_id = job_id
        self._picking_index = self.prefs.last_step_index
        self.root.iconify()
        self.root.after(200, self.picker.show)

    def _ball_open(self) -> None:
        job_id = self.prefs.last_job_id
        self.root.deiconify()
        self.root.lift()
        if job_id:
            self._edit_job(job_id)
        else:
            self._show_home()

    def _ball_select_job(self, job_id: str, name: str) -> None:
        self.prefs.last_job_id = job_id
        self.prefs.last_step_index = 0
        self.store.save_prefs(self.prefs)
        self.status.set(f"当前作业「{name}」")
        self._ball_idle_menu()

    def _ball_moved(self, x: int, y: int) -> None:
        self.prefs.ball_x = x
        self.prefs.ball_y = y
        self.store.save_prefs(self.prefs)


def main() -> None:
    FloatapApp().run()
