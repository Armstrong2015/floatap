package app.floatap.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobRulesTest {
    @Test
    fun intervalBelowMinimumIsRaisedWhenTheDraftIsOtherwiseValid() {
        val checked = checkDraft(sample(intervalMs = 10))
        assertNull(checked.issue)
        assertTrue(checked.intervalAdjusted)
        assertEquals(50L, checked.intervalMs)
    }

    @Test
    fun missingPointIsRejected() {
        val checked = checkDraft(sample(x = -1))
        assertEquals(DraftIssue.NO_POINT, checked.issue)
    }

    @Test
    fun missingPointIsKeptWhenPreparingToPick() {
        val checked = checkDraft(sample(x = -1).copy(allowMissingPoint = true))
        assertNull(checked.issue)
    }

    @Test
    fun rangeKeepsBothEnds() {
        val checked = checkDraft(sample(intervalMs = 400, intervalMaxMs = 900))
        assertNull(checked.issue)
        assertEquals(400L, checked.intervalMs)
        assertEquals(900L, checked.intervalMaxMs)
    }

    @Test
    fun rangeRejectsMaxBelowMin() {
        val checked = checkDraft(sample(intervalMs = 800, intervalMaxMs = 200))
        assertEquals(DraftIssue.INTERVAL_ORDER, checked.issue)
    }

    @Test
    fun missingStopIsRejected() {
        val checked = checkDraft(sample(durationSec = null, clickLimit = null))
        assertEquals(DraftIssue.NO_STOP, checked.issue)
    }

    private fun sample(
        intervalMs: Long = 500,
        intervalMaxMs: Long? = null,
        x: Int = 10,
        durationSec: Long? = 60,
        clickLimit: Int? = null,
    ) = JobDraft(
        name = "作业1",
        x = x,
        y = 20,
        displayWidth = 1080,
        displayHeight = 2400,
        rotation = 0,
        intervalMs = intervalMs,
        intervalMaxMs = intervalMaxMs,
        durationSec = durationSec,
        clickLimit = clickLimit,
        countdownSec = 3,
        pauseOnUserTouch = true,
        existingCount = 0,
        isNew = true,
    )
}
