package app.floatap.android.domain

import java.util.Calendar

const val MIN_SCHEDULE_MINUTE = 0
const val MAX_SCHEDULE_MINUTE = 23 * 60 + 59

fun minutesOfDay(calendar: Calendar = Calendar.getInstance()): Int {
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

fun formatMinutes(minutes: Int): String {
    val clamped = minutes.coerceIn(MIN_SCHEDULE_MINUTE, MAX_SCHEDULE_MINUTE)
    val hour = clamped / 60
    val minute = clamped % 60
    return "%02d:%02d".format(hour, minute)
}

fun parseHourMinute(hourText: String, minuteText: String): Int? {
    val hour = hourText.toIntOrNull() ?: return null
    val minute = minuteText.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun isPastStart(startMinutes: Int, nowMinutes: Int = minutesOfDay()): Boolean {
    return nowMinutes >= startMinutes
}

fun isPastEnd(endMinutes: Int, nowMinutes: Int = minutesOfDay()): Boolean {
    return nowMinutes >= endMinutes
}

/** Milliseconds until today's [startMinutes]. Null if already at or past that minute. */
fun millisUntilStart(startMinutes: Int, nowMillis: Long = System.currentTimeMillis()): Long? {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowMinutes = minutesOfDay(cal)
    if (nowMinutes >= startMinutes) return null
    val target = cal.clone() as Calendar
    target.set(Calendar.HOUR_OF_DAY, startMinutes / 60)
    target.set(Calendar.MINUTE, startMinutes % 60)
    target.set(Calendar.SECOND, 0)
    target.set(Calendar.MILLISECOND, 0)
    val delta = target.timeInMillis - nowMillis
    return delta.takeIf { it > 0 }
}
