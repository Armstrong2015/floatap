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
    LOOP("循环次数要在 1 到 9999"),
    NO_STEPS("至少要有一步"),
}

data class StepDraft(
    val name: String,
    val x: Int,
    val y: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val rotation: Int,
    val intervalMs: Long,
    val intervalMaxMs: Long? = null,
)

data class CheckedStep(
    val name: String,
    val intervalMs: Long,
    val intervalMaxMs: Long,
    val adjusted: Boolean,
)

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
    val steps: List<StepDraft>? = null,
    val loopLimit: Int? = null,
)

data class CheckedDraft(
    val issue: DraftIssue?,
    val issueStep: Int? = null,
    val intervalAdjusted: Boolean,
    val intervalMs: Long,
    val intervalMaxMs: Long,
    val durationMs: Long?,
    val steps: List<CheckedStep> = emptyList(),
    val loopLimit: Int? = null,
) {
    fun message(): String? {
        val found = issue ?: return null
        val at = issueStep ?: return found.message
        return "第 ${at + 1} 步：${found.message}"
    }
}

private data class StepCheck(val issue: DraftIssue?, val step: CheckedStep)

fun checkDraft(draft: JobDraft): CheckedDraft {
    val source = draft.steps ?: listOf(
        StepDraft(
            name = "点击",
            x = draft.x,
            y = draft.y,
            displayWidth = draft.displayWidth,
            displayHeight = draft.displayHeight,
            rotation = draft.rotation,
            intervalMs = draft.intervalMs,
            intervalMaxMs = draft.intervalMaxMs,
        ),
    )
    val checkedSteps = source.map { checkStep(it, draft.allowMissingPoint) }
    val failed = checkedSteps.indexOfFirst { it.issue != null }
    val durationMs = draft.durationSec?.takeIf { it > 0 }?.times(1000)
    val loop = draft.loopLimit
    val issue = when {
        draft.isNew && draft.existingCount >= MAX_JOBS -> DraftIssue.TOO_MANY
        draft.name.isBlank() -> DraftIssue.NAME_EMPTY
        source.isEmpty() -> DraftIssue.NO_STEPS
        failed >= 0 -> checkedSteps[failed].issue
        loop != null && loop !in 1..9999 -> DraftIssue.LOOP
        draft.countdownSec !in 0..10 -> DraftIssue.COUNTDOWN
        durationMs == null && draft.clickLimit == null && loop == null -> DraftIssue.NO_STOP
        else -> null
    }
    val first = checkedSteps.firstOrNull()?.step
    return CheckedDraft(
        issue = issue,
        issueStep = if (failed >= 0 && issue == checkedSteps[failed].issue) failed else null,
        intervalAdjusted = checkedSteps.any { it.step.adjusted } && issue == null,
        intervalMs = first?.intervalMs ?: draft.intervalMs,
        intervalMaxMs = first?.intervalMaxMs ?: (draft.intervalMaxMs ?: draft.intervalMs),
        durationMs = durationMs,
        steps = checkedSteps.map { it.step },
        loopLimit = loop?.takeIf { it in 1..9999 },
    )
}

private fun checkStep(step: StepDraft, allowMissingPoint: Boolean): StepCheck {
    val adjusted = step.intervalMs in 0 until MIN_INTERVAL_MS
    val interval = if (adjusted) MIN_INTERVAL_MS else step.intervalMs
    val maxRaw = step.intervalMaxMs
    val maxAdjusted = maxRaw != null && maxRaw in 0 until MIN_INTERVAL_MS
    val intervalMax = when {
        maxRaw == null -> interval
        maxAdjusted -> MIN_INTERVAL_MS
        else -> maxRaw
    }
    val issue = when {
        !allowMissingPoint && (step.x < 0 || step.y < 0 || step.displayWidth <= 0 || step.displayHeight <= 0) -> DraftIssue.NO_POINT
        step.intervalMs < 0 || step.intervalMs > MAX_INTERVAL_MS -> DraftIssue.INTERVAL
        maxRaw != null && maxRaw < 0 -> DraftIssue.INTERVAL_MAX
        maxRaw != null && maxRaw > MAX_INTERVAL_MS -> DraftIssue.INTERVAL
        maxRaw != null && intervalMax < interval -> DraftIssue.INTERVAL_ORDER
        else -> null
    }
    return StepCheck(
        issue = issue,
        step = CheckedStep(
            name = step.name.ifBlank { "点击" },
            intervalMs = interval,
            intervalMaxMs = intervalMax,
            adjusted = adjusted || maxAdjusted,
        ),
    )
}

fun displayMatches(step: Step, current: DisplaySnapshot): Boolean {
    return step.displayWidth == current.width &&
        step.displayHeight == current.height &&
        step.rotation == current.rotation
}
