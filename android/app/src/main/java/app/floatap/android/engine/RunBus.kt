package app.floatap.android.engine

import app.floatap.android.domain.RunPhase
import kotlinx.coroutines.flow.MutableStateFlow

object RunBus {
    val phase = MutableStateFlow(RunPhase.IDLE)
    val message = MutableStateFlow<String?>(null)

    @Volatile
    var pauseOnUserTouch: Boolean = true

    @Volatile
    var activeJobId: Long = 0L

    @Volatile
    var ignoreUserTouchUntil: Long = 0L
}
