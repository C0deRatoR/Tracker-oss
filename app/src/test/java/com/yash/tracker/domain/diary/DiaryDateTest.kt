package com.yash.tracker.domain.diary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DiaryDateTest {

    private val mumbai = ZoneId.of("Asia/Kolkata")

    private fun at(isoLocal: String): Instant =
        java.time.LocalDateTime.parse(isoLocal).atZone(mumbai).toInstant()

    @Test
    fun `a late-night snack belongs to the day before`() {
        assertEquals("2026-09-12", DiaryDate.resolveKey(at("2026-09-13T01:00"), mumbai, 4))
    }

    @Test
    fun `the day-start hour itself begins the new day`() {
        assertEquals("2026-09-13", DiaryDate.resolveKey(at("2026-09-13T04:00"), mumbai, 4))
    }

    @Test
    fun `an ordinary lunch belongs to the same day`() {
        assertEquals("2026-09-13", DiaryDate.resolveKey(at("2026-09-13T13:30"), mumbai, 4))
    }

    @Test
    fun `just before midnight still belongs to that day`() {
        assertEquals("2026-09-13", DiaryDate.resolveKey(at("2026-09-13T23:59"), mumbai, 4))
    }

    @Test
    fun `a day-start of zero makes midnight the boundary`() {
        assertEquals("2026-09-13", DiaryDate.resolveKey(at("2026-09-13T00:05"), mumbai, 0))
    }
}
