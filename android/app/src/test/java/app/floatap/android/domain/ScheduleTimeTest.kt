package app.floatap.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleTimeTest {
    @Test
    fun formatAndParseRoundTrip() {
        assertEquals("09:05", formatMinutes(9 * 60 + 5))
        assertEquals(9 * 60 + 5, parseHourMinute("09", "05"))
        assertNull(parseHourMinute("24", "00"))
        assertNull(parseHourMinute("10", "60"))
    }

    @Test
    fun pastStartAndEnd() {
        assertTrue(isPastStart(10 * 60, nowMinutes = 10 * 60))
        assertTrue(isPastStart(10 * 60, nowMinutes = 11 * 60))
        assertFalse(isPastStart(10 * 60, nowMinutes = 9 * 60 + 59))
        assertTrue(isPastEnd(18 * 60, nowMinutes = 18 * 60))
        assertFalse(isPastEnd(18 * 60, nowMinutes = 17 * 60 + 59))
    }

    @Test
    fun millisUntilStartNullWhenPast() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertNull(millisUntilStart(15 * 60, cal.timeInMillis))
        assertNull(millisUntilStart(14 * 60, cal.timeInMillis))
    }

    @Test
    fun millisUntilStartPositiveWhenFuture() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delta = millisUntilStart(9 * 60, cal.timeInMillis)
        assertEquals(60 * 60 * 1000L, delta)
    }

    @Test
    fun minutesOfDayFromCalendar() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 45)
        }
        assertEquals(13 * 60 + 45, minutesOfDay(cal))
    }
}
