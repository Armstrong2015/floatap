package app.floatap.android.domain

import kotlin.math.abs
import kotlin.math.max

enum class ColorMismatchAction {
    SKIP,
    STOP,
}

data class ColorCondition(
    val x: Int,
    val y: Int,
    val sampleArgb: Int,
    val tolerance: Int = 32,
    val onMismatch: ColorMismatchAction = ColorMismatchAction.STOP,
)

fun colorChannelDistance(sampleArgb: Int, actualArgb: Int): Int {
    val sr = (sampleArgb shr 16) and 0xff
    val sg = (sampleArgb shr 8) and 0xff
    val sb = sampleArgb and 0xff
    val ar = (actualArgb shr 16) and 0xff
    val ag = (actualArgb shr 8) and 0xff
    val ab = actualArgb and 0xff
    return max(abs(sr - ar), max(abs(sg - ag), abs(sb - ab)))
}

fun colorMatches(sampleArgb: Int, actualArgb: Int, tolerance: Int): Boolean {
    return colorChannelDistance(sampleArgb, actualArgb) <= tolerance.coerceAtLeast(0)
}
