package app.floatap.android.domain

enum class WaitMode { FIXED, RANGE }

enum class RunPhase { IDLE, ARMED, RUNNING, PAUSED }

enum class StopReason {
    USER,
    DURATION,
    CLICK_LIMIT,
    LOOP_LIMIT,
    SCHEDULE_END,
    PERMISSION,
    DISPLAY_CHANGED,
    COLOR_MISMATCH,
}

data class Step(
    val id: Long = 0,
    val order: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val rotation: Int,
    val waitMode: WaitMode = WaitMode.FIXED,
    val waitMinMs: Long,
    val waitMaxMs: Long,
)

data class Job(
    val id: Long = 0,
    val name: String,
    val steps: List<Step>,
    val durationMs: Long?,
    val clickLimit: Int?,
    val loopLimit: Int? = null,
    val scheduleStartMinutes: Int? = null,
    val scheduleEndMinutes: Int? = null,
    val countdownSec: Int = 3,
    val pauseOnUserTouch: Boolean = true,
    val updatedAt: Long = 0,
)

data class JobSummary(
    val id: Long,
    val name: String,
    val stepCount: Int,
    val updatedAt: Long,
)

data class DisplaySnapshot(
    val width: Int,
    val height: Int,
    val rotation: Int,
)
