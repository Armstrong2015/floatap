package app.floatap.android.engine

import app.floatap.android.domain.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    @Test
    fun durationDoesNotStopBeforeTheFirstClick() {
        val reason = nextStop(10_000, null, null, Progress(), nowElapsed = 10_000)
        assertNull(reason)
    }

    @Test
    fun durationStopsBeforeTheNextClick() {
        val reason = nextStop(
            durationMs = 10_000,
            clickLimit = null,
            loopLimit = null,
            progress = Progress(clicks = 1, firstClickElapsed = 1_000),
            nowElapsed = 11_000,
        )
        assertEquals(StopReason.DURATION, reason)
    }

    @Test
    fun clickLimitStopsWhenTheCapIsAlreadyReached() {
        val reason = nextStop(null, clickLimit = 10, loopLimit = null, Progress(clicks = 10), nowElapsed = 0)
        assertEquals(StopReason.CLICK_LIMIT, reason)
    }

    @Test
    fun closedGateRejectsALateClick() {
        val gate = ClickGate()
        gate.open(7)
        assertTrue(gate.allows(7))
        gate.close()
        assertFalse(gate.allows(7))
    }

    @Test
    fun mismatchedRunIdIsRejected() {
        val gate = ClickGate()
        gate.open(1)
        assertFalse(gate.allows(2))
    }
}
