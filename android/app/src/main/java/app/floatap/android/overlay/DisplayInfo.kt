package app.floatap.android.overlay

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import app.floatap.android.domain.DisplaySnapshot

fun Context.readDisplay(): DisplaySnapshot {
    // A service has no visual Context. Context.display throws instead of returning null.
    val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val size = Point()
    @Suppress("DEPRECATION")
    display.getRealSize(size)
    return DisplaySnapshot(size.x, size.y, display.rotation)
}
