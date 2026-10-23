package app.floatap.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class JobNamesTest {
    @Test
    fun keepsFreeName() {
        val taken = mutableSetOf("其它")
        assertEquals("每日", allocateJobName("每日", taken))
        assertEquals(setOf("其它", "每日"), taken)
    }

    @Test
    fun appendsNumericSuffixOnClash() {
        val taken = mutableSetOf("每日", "每日2")
        assertEquals("每日3", allocateJobName("每日", taken))
        assertEquals("每日4", allocateJobName("每日", taken))
    }

    @Test
    fun blankBecomesJob() {
        val taken = mutableSetOf<String>()
        assertEquals("作业", allocateJobName("  ", taken))
        assertEquals("作业2", allocateJobName("", taken))
    }
}
