package app.floatap.android.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import app.floatap.android.domain.DisplaySnapshot

fun Context.readDisplay(): DisplaySnapshot {
    val wm = getSystemService(WindowManager::class.java)
    val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: wm.defaultDisplay.rotation
    } else {
        @Suppress("DEPRECATION")
        wm.defaultDisplay.rotation
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        return DisplaySnapshot(bounds.width(), bounds.height(), rotation)
    }
    val point = Point()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealSize(point)
    return DisplaySnapshot(point.x, point.y, rotation)
}
