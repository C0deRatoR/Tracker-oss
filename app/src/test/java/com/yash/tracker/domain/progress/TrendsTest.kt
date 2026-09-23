package com.yash.tracker.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrendsTest {

    private val day = LocalDate.of(2026, 9, 1)

    private fun points(vararg pairs: Pair<Int, Double>) =
        pairs.map { (offset, value) -> DayPoint(day.plusDays(offset.toLong()), value) }

    @Test
    fun `a moving average smooths a daily series`() {
        val averaged = MovingAverage.overDays(points(0 to 80.0, 1 to 82.0, 2 to 78.0), days = 3)

        assertEquals(80.0, averaged[0].value, 0.001)
        assertEquals(81.0, averaged[1].value, 0.001)
        assertEquals(80.0, averaged[2].value, 0.001)
    }

    @Test
    fun `a gap in weigh-ins does not drag an old number into the average`() {
        // Weighed 85 in September, then nothing until October. The October average must be
        // October's number alone, not a blend with a month-old reading.
        val averaged = MovingAverage.overDays(points(0 to 85.0, 30 to 80.0), days = 7)

        assertEquals(85.0, averaged[0].value, 0.001)
        assertEquals(80.0, averaged[1].value, 0.001)
    }

    @Test
    fun `two weigh-ins on consecutive days both count`() {
        val averaged = MovingAverage.overDays(points(0 to 80.0, 1 to 84.0), days = 7)

        assertEquals(82.0, averaged[1].value, 0.001)
    }

    @Test
    fun `adherence counts days inside the band on either side`() {
        val days = points(0 to 2000.0, 1 to 2150.0, 2 to 1850.0, 3 to 2600.0)

        // 2000 target, 10% band: 1800-2200. Three of four days land inside it.
        assertEquals(75, Adherence.percent(days, targetKcal = 2000))
    }

    @Test
    fun `barely eating is not adherence`() {
        val days = points(0 to 500.0, 1 to 600.0)

        assertEquals(0, Adherence.percent(days, targetKcal = 2000))
    }

    @Test
    fun `adherence with nothing logged is zero rather than a crash`() {
        assertEquals(0, Adherence.percent(emptyList(), targetKcal = 2000))
        assertEquals(0, Adherence.percent(points(0 to 2000.0), targetKcal = 0))
    }

    @Test
    fun `a streak counts back from today`() {
        val logged = setOf(day, day.minusDays(1), day.minusDays(2), day.minusDays(4))

        assertEquals(3, Streak.current(logged, today = day))
    }

    @Test
    fun `today being empty does not break the streak yet`() {
        val logged = setOf(day.minusDays(1), day.minusDays(2))

        assertEquals(2, Streak.current(logged, today = day))
    }

    @Test
    fun `a gap of two days ends the streak`() {
        val logged = setOf(day.minusDays(2), day.minusDays(3))

        assertEquals(0, Streak.current(logged, today = day))
    }
}
