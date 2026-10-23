package app.floatap.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorConditionTest {
    @Test
    fun identicalColorsMatch() {
        val color = 0xFF336699.toInt()
        assertTrue(colorMatches(color, color, 0))
    }

    @Test
    fun withinToleranceMatches() {
        val sample = 0xFF100000.toInt()
        val actual = 0xFF100014.toInt()
        assertTrue(colorMatches(sample, actual, 20))
        assertFalse(colorMatches(sample, actual, 10))
    }
}
