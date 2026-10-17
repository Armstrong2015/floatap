package app.floatap.android.engine

import app.floatap.android.domain.StopReason

data class Progress(
    val clicks: Int = 0,
    val loops: Int = 0,
    val firstClickElapsed: Long? = null,
)

fun nextStop(
    durationMs: Long?,
    clickLimit: Int?,
    loopLimit: Int?,
    progress: Progress,
    nowElapsed: Long,
): StopReason? {
    if (clickLimit != null && progress.clicks >= clickLimit) return StopReason.CLICK_LIMIT
    if (loopLimit != null && progress.loops >= loopLimit) return StopReason.LOOP_LIMIT
    val first = progress.firstClickElapsed
    if (durationMs != null && first != null && nowElapsed - first >= durationMs) {
        return StopReason.DURATION
    }
    return null
}
