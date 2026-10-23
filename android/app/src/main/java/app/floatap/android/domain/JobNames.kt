package app.floatap.android.domain

/**
 * Pick a display name that is not yet in [taken].
 * If [base] is free, use it; otherwise append 2, 3, … (`每日` → `每日2`).
 * Newly chosen names are added to [taken].
 */
fun allocateJobName(base: String, taken: MutableSet<String>): String {
    val trimmed = base.trim().ifEmpty { "作业" }
    if (trimmed !in taken) {
        taken += trimmed
        return trimmed
    }
    var n = 2
    while (true) {
        val candidate = "$trimmed$n"
        if (candidate !in taken) {
            taken += candidate
            return candidate
        }
        n += 1
    }
}
