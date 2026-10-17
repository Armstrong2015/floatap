package app.floatap.android.engine

import app.floatap.android.domain.StopReason

data class PlannedClick(
    val stepIndex: Int,
    val atMs: Long,
)

data class ScheduleInput(
    val waitsMs: List<Long>,
    val countdownSec: Int = 3,
    val durationMs: Long? = null,
    val clickLimit: Int? = null,
    val loopLimit: Int? = null,
)

data class ScheduleResult(
    val clicks: List<PlannedClick>,
    val stop: StopReason,
    val finishedAtMs: Long,
)

/**
 * Same order as [FloatRunService]: countdown, then for each step wait, check
 * [nextStop], click, and stop when the click limit, duration, or loop limit is met.
 * The clock is exact, so this is the reference the device loop is measured against.
 */
fun schedule(input: ScheduleInput): ScheduleResult {
    require(input.waitsMs.isNotEmpty()) { "schedule needs at least one step" }
    val clicks = mutableListOf<PlannedClick>()
    var t = input.countdownSec.coerceAtLeast(0) * 1000L
    var index = 0
    var loops = 0
    var progress = Progress()
    while (true) {
        t += input.waitsMs[index]
        val reason = nextStop(input.durationMs, input.clickLimit, input.loopLimit, progress, t)
        if (reason != null) {
            return ScheduleResult(clicks, reason, t)
        }
        clicks += PlannedClick(index, t)
        val first = progress.firstClickElapsed ?: t
        val count = progress.clicks + 1
        val wrapped = index + 1 >= input.waitsMs.size
        val nextIndex = if (wrapped) 0 else index + 1
        if (wrapped) loops += 1
        progress = Progress(count, loops, first)
        index = nextIndex
        if (input.loopLimit != null && loops >= input.loopLimit && nextIndex == 0) {
            return ScheduleResult(clicks, StopReason.LOOP_LIMIT, t)
        }
        val after = nextStop(input.durationMs, input.clickLimit, input.loopLimit, progress, t)
        if (after == StopReason.CLICK_LIMIT || after == StopReason.DURATION) {
            return ScheduleResult(clicks, after, t)
        }
    }
}
