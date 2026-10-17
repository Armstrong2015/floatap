package app.floatap.android

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import app.floatap.android.a11y.FloatAccessibilityService

fun Context.canDrawOverlay(): Boolean = Settings.canDrawOverlays(this)

fun Context.isAccessibilityEnabled(): Boolean {
    val expected = ComponentName(this, FloatAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
