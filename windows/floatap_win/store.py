from __future__ import annotations

import json
import time
import uuid
from pathlib import Path
from typing import Optional

from .models import Job, JobSummary, Prefs, Step, WaitMode


def app_dir() -> Path:
    root = Path.home() / "AppData" / "Roaming" / "Floatap"
    root.mkdir(parents=True, exist_ok=True)
    (root / "jobs").mkdir(exist_ok=True)
    return root


class JobStore:
    def __init__(self, root: Optional[Path] = None) -> None:
        self.root = root or app_dir()
        self.jobs_dir = self.root / "jobs"
        self.prefs_path = self.root / "prefs.json"
        self.jobs_dir.mkdir(parents=True, exist_ok=True)

    def prefs(self) -> Prefs:
        if not self.prefs_path.exists():
            return Prefs()
        raw = json.loads(self.prefs_path.read_text(encoding="utf-8"))
        return Prefs(
            boundary_accepted=bool(raw.get("boundaryAccepted", False)),
            last_job_id=str(raw.get("lastJobId", "")),
            last_step_index=int(raw.get("lastStepIndex", 0)),
            ball_x=int(raw.get("ballX", 24)),
            ball_y=int(raw.get("ballY", 240)),
        )

    def save_prefs(self, prefs: Prefs) -> None:
        payload = {
            "boundaryAccepted": prefs.boundary_accepted,
            "lastJobId": prefs.last_job_id,
            "lastStepIndex": prefs.last_step_index,
            "ballX": prefs.ball_x,
            "ballY": prefs.ball_y,
        }
        self.prefs_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    def summaries(self) -> list[JobSummary]:
        items: list[JobSummary] = []
        for path in sorted(self.jobs_dir.glob("*.json")):
            try:
                job = self.get(path.stem)
            except Exception:
                continue
            if job is None:
                continue
            items.append(
                JobSummary(
                    id=job.id,
                    name=job.name,
                    step_count=len(job.steps),
                    updated_at=job.updated_at,
                )
            )
        items.sort(key=lambda item: item.updated_at, reverse=True)
        return items

    def count(self) -> int:
        return len(list(self.jobs_dir.glob("*.json")))

    def get(self, job_id: str) -> Optional[Job]:
        path = self.jobs_dir / f"{job_id}.json"
        if not path.exists():
            return None
        raw = json.loads(path.read_text(encoding="utf-8"))
        return Job.from_json(raw, fallback_id=job_id)

    def save(self, job: Job) -> str:
        job_id = job.id or uuid.uuid4().hex[:12]
        now = int(time.time() * 1000)
        saved = Job(
            id=job_id,
            name=job.name,
            steps=[
                Step(
                    order=i,
                    name=step.name,
                    x=step.x,
                    y=step.y,
                    display_width=step.display_width,
                    display_height=step.display_height,
                    rotation=step.rotation,
                    wait_mode=step.wait_mode,
                    wait_min_ms=step.wait_min_ms,
                    wait_max_ms=step.wait_max_ms,
                )
                for i, step in enumerate(job.steps)
            ],
            duration_ms=job.duration_ms,
            click_limit=job.click_limit,
            loop_limit=job.loop_limit,
            countdown_sec=job.countdown_sec,
            pause_on_user_touch=job.pause_on_user_touch,
            updated_at=now,
        )
        path = self.jobs_dir / f"{job_id}.json"
        path.write_text(
            json.dumps(saved.to_json(), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return job_id

    def delete(self, job_id: str) -> None:
        path = self.jobs_dir / f"{job_id}.json"
        if path.exists():
            path.unlink()

    def new_job(self, name: str = "") -> Job:
        label = name.strip() or f"作业{self.count() + 1}"
        return Job(
            id="",
            name=label,
            steps=[
                Step(
                    order=0,
                    name="点击",
                    x=-1,
                    y=-1,
                    display_width=0,
                    display_height=0,
                    wait_mode=WaitMode.FIXED,
                    wait_min_ms=500,
                    wait_max_ms=500,
                )
            ],
            duration_ms=60_000,
            click_limit=None,
            loop_limit=None,
            countdown_sec=3,
            pause_on_user_touch=False,
        )
