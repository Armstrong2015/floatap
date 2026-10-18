package app.floatap.android.domain

const val MIN_INTERVAL_MS = 50L
const val MAX_INTERVAL_MS = 3_600_000L
const val MAX_JOBS = 50

enum class DraftIssue(val message: String) {
    NAME_EMPTY("写一个作业名称"),
    NO_POINT("先选定点击位置"),
    INTERVAL("间隔要在 50 毫秒到 1 小时之间"),
    INTERVAL_MAX("填写最大间隔"),
    INTERVAL_ORDER("最大间隔不能小于最小间隔"),
    NO_STOP("填写持续时长或点击次数，避免一直点下去"),
    COUNTDOWN("倒计时要在 0 到 10 秒"),
    TOO_MANY("作业已有 50 个，先删除或等导出功能"),
}

data class JobDraft(
    val name: String,
    val x: Int,
    val y: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val rotation: Int,
    val intervalMs: Long,
    val intervalMaxMs: Long? = null,
    val durationSec: Long?,
    val clickLimit: Int?,
    val countdownSec: Int,
    val pauseOnUserTouch: Boolean,
    val existingCount: Int,
    val isNew: Boolean,
    val allowMissingPoint: Boolean = false,
)

data class CheckedDraft(
    val issue: DraftIssue?,
    val intervalAdjusted: Boolean,
    val intervalMs: Long,
    val intervalMaxMs: Long,
    val durationMs: Long?,
)

fun checkDraft(draft: JobDraft): CheckedDraft {
    val adjusted = draft.intervalMs in 0 until MIN_INTERVAL_MS
    val interval = if (adjusted) MIN_INTERVAL_MS else draft.intervalMs
    val maxRaw = draft.intervalMaxMs
    val maxAdjusted = maxRaw != null && maxRaw in 0 until MIN_INTERVAL_MS
    val intervalMax = when {
        maxRaw == null -> interval
        maxAdjusted -> MIN_INTERVAL_MS
        else -> maxRaw
    }
    val durationMs = draft.durationSec?.takeIf { it > 0 }?.times(1000)
    val issue = when {
        draft.isNew && draft.existingCount >= MAX_JOBS -> DraftIssue.TOO_MANY
        draft.name.isBlank() -> DraftIssue.NAME_EMPTY
        !draft.allowMissingPoint && (draft.x < 0 || draft.y < 0 || draft.displayWidth <= 0 || draft.displayHeight <= 0) -> DraftIssue.NO_POINT
        draft.intervalMs < 0 || draft.intervalMs > MAX_INTERVAL_MS -> DraftIssue.INTERVAL
        maxRaw != null && maxRaw < 0 -> DraftIssue.INTERVAL_MAX
        maxRaw != null && maxRaw > MAX_INTERVAL_MS -> DraftIssue.INTERVAL
        maxRaw != null && intervalMax < interval -> DraftIssue.INTERVAL_ORDER
        draft.countdownSec !in 0..10 -> DraftIssue.COUNTDOWN
        durationMs == null && draft.clickLimit == null -> DraftIssue.NO_STOP
        else -> null
    }
    return CheckedDraft(
        issue = issue,
        intervalAdjusted = (adjusted || maxAdjusted) && issue == null,
        intervalMs = interval,
        intervalMaxMs = intervalMax,
        durationMs = durationMs,
    )
}

fun displayMatches(step: Step, current: DisplaySnapshot): Boolean {
    return step.displayWidth == current.width &&
        step.displayHeight == current.height &&
        step.rotation == current.rotation
}
