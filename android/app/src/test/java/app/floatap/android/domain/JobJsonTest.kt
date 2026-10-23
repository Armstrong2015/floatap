package app.floatap.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobJsonTest {
    @Test
    fun roundTripKeepsScheduleAndColor() {
        val job = Job(
            name = "每日",
            steps = listOf(
                Step(
                    order = 0,
                    name = "领取",
                    x = 10,
                    y = 20,
                    displayWidth = 1080,
                    displayHeight = 2400,
                    rotation = 0,
                    waitMinMs = 400,
                    waitMaxMs = 600,
                    waitMode = WaitMode.RANGE,
                    color = ColorCondition(
                        x = 10,
                        y = 20,
                        sampleArgb = 0xFF112233.toInt(),
                        tolerance = 24,
                        onMismatch = ColorMismatchAction.SKIP,
                    ),
                ),
            ),
            durationMs = 60_000,
            clickLimit = null,
            loopLimit = 2,
            scheduleStartMinutes = 9 * 60,
            scheduleEndMinutes = 18 * 60,
            countdownSec = 2,
        )
        val parsed = jobFromJson(jobToJson(job))
        assertEquals("每日", parsed.name)
        assertEquals(2, parsed.loopLimit)
        assertEquals(9 * 60, parsed.scheduleStartMinutes)
        assertEquals(18 * 60, parsed.scheduleEndMinutes)
        assertEquals(WaitMode.RANGE, parsed.steps[0].waitMode)
        assertEquals(0xFF112233.toInt(), parsed.steps[0].color?.sampleArgb)
        assertEquals(ColorMismatchAction.SKIP, parsed.steps[0].color?.onMismatch)
    }

    @Test
    fun rejectsUnknownFormat() {
        try {
            jobFromJson("""{"format":2,"name":"x","steps":[]}""")
            throw AssertionError("expected failure")
        } catch (error: JobJsonException) {
            assertTrue(error.message!!.contains("format"))
        }
    }

    @Test
    fun nullScheduleStaysNull() {
        val job = Job(
            name = "单点",
            steps = listOf(
                Step(
                    order = 0,
                    name = "点击",
                    x = 1,
                    y = 2,
                    displayWidth = 100,
                    displayHeight = 200,
                    rotation = 0,
                    waitMinMs = 500,
                    waitMaxMs = 500,
                ),
            ),
            durationMs = 10_000,
            clickLimit = null,
        )
        val parsed = jobFromJson(jobToJson(job))
        assertNull(parsed.scheduleStartMinutes)
        assertNull(parsed.steps[0].color)
    }
}
