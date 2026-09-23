package com.yash.tracker.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the Time cell does with what you type.
 *
 * A treadmill walk was logged as five seconds because a bare "5" was read as seconds and the
 * cell reformatted it to "0:05" before a colon could be typed. Minutes is the only reading
 * that matches what the column is for.
 */
class ClockEntryTest {

    @Test
    fun `a bare number is minutes`() {
        assertEquals(300, parseClock("5"))
        assertEquals(1800, parseClock("30"))
    }

    @Test
    fun `minutes and seconds are read as written`() {
        assertEquals(330, parseClock("5:30"))
        assertEquals(5, parseClock("0:05"))
        assertEquals(3600, parseClock("60:00"))
    }

    @Test
    fun `a half-typed colon holds the minutes it already has`() {
        assertEquals(300, parseClock("5:"))
    }

    @Test
    fun `nothing usable reads as nothing`() {
        assertNull(parseClock(""))
        assertNull(parseClock("abc"))
        assertNull(parseClock("1:2:3"))
    }

    @Test
    fun `what is shown parses back to what was stored`() {
        for (seconds in listOf(5, 60, 300, 330, 3599)) {
            assertEquals(
                "round trip of $seconds",
                seconds,
                parseClock(formatClock(seconds)),
            )
        }
    }
}
