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

    @Test
    fun loopCountAloneCanStop() {
        val checked = checkDraft(
            sample(durationSec = null, clickLimit = null).copy(
                loopLimit = 2,
                steps = listOf(step(100), step(200)),
            ),
        )
        assertNull(checked.issue)
        assertEquals(2, checked.loopLimit)
        assertEquals(listOf(100L, 200L), checked.steps.map { it.intervalMs })
    }

    @Test
    fun secondStepWithoutAPointIsNamed() {
        val checked = checkDraft(
            sample().copy(
                steps = listOf(step(500), step(500, x = -1)),
            ),
        )
        assertEquals(DraftIssue.NO_POINT, checked.issue)
        assertEquals(1, checked.issueStep)
        assertEquals("第 2 步：先选定点击位置", checked.message())
    }

    @Test
    fun loopOutsideRangeIsRejected() {
        val checked = checkDraft(sample().copy(loopLimit = 0))
        assertEquals(DraftIssue.LOOP, checked.issue)
    }

    private fun step(intervalMs: Long, x: Int = 10) = StepDraft(
        name = "点击",
        x = x,
        y = 20,
        displayWidth = 1080,
        displayHeight = 2400,
        rotation = 0,
        intervalMs = intervalMs,
    )

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
