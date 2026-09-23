package com.yash.tracker.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartScaleTest {

    private val day = LocalDate.of(2026, 9, 1)

    private fun points(vararg pairs: Pair<Int, Double>) =
        pairs.map { (offset, value) -> DayPoint(day.plusDays(offset.toLong()), value) }

    @Test
    fun `an empty series has no scale to draw`() {
        assertNull(ChartScale.of(emptyList()))
    }

    @Test
    fun `the first and last day sit at the edges`() {
        val series = points(0 to 80.0, 6 to 78.0)
        val scale = ChartScale.of(series)!!

        assertEquals(0f, scale.x(series.first()), 0.001f)
        assertEquals(1f, scale.x(series.last()), 0.001f)
    }

    @Test
    fun `a gap in dates is drawn as a gap, not an even step`() {
        val series = points(0 to 80.0, 1 to 79.0, 30 to 78.0)
        val scale = ChartScale.of(series)!!

        // Day 1 of 30 belongs near the left edge. Spacing by index would have put it halfway.
        assertEquals(1f / 30f, scale.x(series[1]), 0.001f)
    }

    @Test
    fun `a single point does not divide by zero`() {
        val series = points(0 to 80.0)
        val scale = ChartScale.of(series)!!

        assertEquals(0f, scale.x(series.single()), 0.001f)
        assertTrue(scale.y(80.0).isFinite())
    }

    @Test
    fun `a flat series does not divide by zero`() {
        val series = points(0 to 80.0, 1 to 80.0, 2 to 80.0)
        val scale = ChartScale.of(series)!!

        assertTrue(scale.y(80.0).isFinite())
        assertEquals("a flat line sits in the middle", 0.5f, scale.y(80.0), 0.001f)
    }

    @Test
    fun `every value lands inside the chart with room to spare`() {
        val series = points(0 to 80.0, 1 to 74.0, 2 to 86.0)
        val scale = ChartScale.of(series)!!

        series.forEach {
            val y = scale.y(it.value)
            assertTrue("$y is outside the chart", y in 0f..1f)
        }
        assertTrue("the highest point should not touch the edge", scale.y(86.0) < 1f)
        assertTrue("nor the lowest", scale.y(74.0) > 0f)
    }

    @Test
    fun `a goal line far below the data is still on the chart`() {
        val series = points(0 to 80.0, 1 to 79.0)
        val scale = ChartScale.of(series, extraValues = listOf(72.0))!!

        assertTrue(scale.y(72.0) in 0f..1f)
        assertTrue("the goal sits below the readings", scale.y(72.0) < scale.y(79.0))
    }
}
