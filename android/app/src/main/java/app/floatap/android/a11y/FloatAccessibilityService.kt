package app.floatap.android.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import app.floatap.android.domain.RunPhase
import app.floatap.android.engine.ClickGate
import app.floatap.android.engine.FloatRunService
import app.floatap.android.engine.RunBus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FloatAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        ClickBridge.service = this
    }

    override fun onDestroy() {
        if (ClickBridge.service === this) ClickBridge.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) return
        val phase = RunBus.phase.value
        if (phase != RunPhase.RUNNING && phase != RunPhase.ARMED) return
        if (!RunBus.pauseOnUserTouch) return
        val now = System.currentTimeMillis()
        val touchedAt = event.eventTime
        if (touchedAt in ClickBridge.ownGestureStartedAt..ClickBridge.ownGestureUntil) return
        if (now < RunBus.ignoreUserTouchUntil) return
        FloatRunService.pause(this)
    }

    override fun onInterrupt() = Unit

    suspend fun tap(x: Int, y: Int, runId: Long): Boolean {
        if (!ClickBridge.gate.allows(runId)) return false
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x + 1f, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        if (!ClickBridge.gate.allows(runId)) return false
        val started = android.os.SystemClock.uptimeMillis()
        ClickBridge.ownGestureStartedAt = started
        ClickBridge.ownGestureUntil = started + TAP_DURATION_MS + 80
        return suspendCancellableCoroutine { cont ->
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!accepted && cont.isActive) cont.resume(false)
        }
    }

    companion object {
        // Shorter strokes are dropped by the gesture dispatcher on many devices.
        const val TAP_DURATION_MS = 50L
    }
}

object ClickBridge {
    val gate = ClickGate()

    @Volatile
    var service: FloatAccessibilityService? = null

    @Volatile
    var ownGestureStartedAt: Long = 0L

    @Volatile
    var ownGestureUntil: Long = 0L

    suspend fun tap(x: Int, y: Int, runId: Long): Boolean {
        val current = service ?: return false
        return current.tap(x, y, runId)
    }
}
