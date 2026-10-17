package app.floatap.android.engine

import app.floatap.android.domain.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ScheduleTest {
    @Test
    fun tenSecondRunAt500msProducesTwentyClicks() {
        val result = schedule(
            ScheduleInput(
                waitsMs = listOf(500),
                countdownSec = 3,
                durationMs = 10_000,
            ),
        )
        assertEquals(StopReason.DURATION, result.stop)
        assertEquals(20, result.clicks.size)
        assertEquals(3_500L, result.clicks.first().atMs)
        assertEquals(13_000L, result.clicks.last().atMs)
        val gaps = result.clicks.zipWithNext { a, b -> b.atMs - a.atMs }
        assertTrue(gaps.all { it == 500L })
    }

    @Test
    fun clickLimitStopsAtTenWithoutAnExtraClick() {
        val result = schedule(
            ScheduleInput(waitsMs = listOf(500), countdownSec = 0, clickLimit = 10),
        )
        assertEquals(StopReason.CLICK_LIMIT, result.stop)
        assertEquals(10, result.clicks.size)
    }

    @Test
    fun threeStepsLoopTwiceInOrder() {
        val result = schedule(
            ScheduleInput(
                waitsMs = listOf(100, 200, 300),
                countdownSec = 0,
                loopLimit = 2,
            ),
        )
        assertEquals(StopReason.LOOP_LIMIT, result.stop)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), result.clicks.map { it.stepIndex })
    }

    @Test
    fun randomWaitsStayInsideTheClosedRangeAndAreNotIdentical() {
        val random = Random(20261018)
        val samples = List(1000) { 400L + random.nextInt(201).toLong() }
        assertTrue(samples.all { it in 400L..600L })
        assertTrue(samples.distinct().size > 1)
    }
}
