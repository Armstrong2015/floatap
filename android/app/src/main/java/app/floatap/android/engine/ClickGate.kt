package app.floatap.android.engine

import java.util.concurrent.atomic.AtomicBoolean

class ClickGate {
    private val open = AtomicBoolean(false)
    private var runId = 0L

    fun open(id: Long) {
        runId = id
        open.set(true)
    }

    fun close() {
        open.set(false)
    }

    fun allows(id: Long): Boolean = open.get() && runId == id
}
