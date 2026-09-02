package ru.maxstrix.workbalance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class WorkTimeCalculatorTest {
    private val date = LocalDate.of(2026, 9, 2)
    private val full = Schedule(workMinutes = 480, lunchMinutes = 60)

    private fun event(hour: Int, minute: Int, type: EventType, id: Long) =
        WorkEvent(id, LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute), type)

    @Test
    fun `nine hours on site with no exit credits eight hours`() {
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(event(9, 0, EventType.IN, 1), event(18, 0, EventType.OUT, 2)),
            full,
            now = date.atTime(19, 0)
        )
        assertEquals(540, result.presenceMinutes)
        assertEquals(60, result.deductedLunchMinutes)
        assertEquals(480, result.creditedMinutes)
    }

    @Test
    fun `one hour outside counts as lunch and is not deducted twice`() {
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(
                event(9, 0, EventType.IN, 1), event(13, 0, EventType.OUT, 2),
                event(14, 0, EventType.IN, 3), event(18, 0, EventType.OUT, 4)
            ),
            full,
            now = date.atTime(19, 0)
        )
        assertEquals(480, result.presenceMinutes)
        assertEquals(60, result.outsideMinutes)
        assertEquals(0, result.deductedLunchMinutes)
        assertEquals(480, result.creditedMinutes)
    }

    @Test
    fun `twenty minute exit deducts remaining forty minutes`() {
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(
                event(9, 0, EventType.IN, 1), event(13, 0, EventType.OUT, 2),
                event(13, 20, EventType.IN, 3), event(18, 0, EventType.OUT, 4)
            ),
            full,
            now = date.atTime(19, 0)
        )
        assertEquals(520, result.presenceMinutes)
        assertEquals(20, result.outsideMinutes)
        assertEquals(40, result.deductedLunchMinutes)
        assertEquals(480, result.creditedMinutes)
    }

    @Test
    fun `ninety minute exit permits thirty minutes overtime`() {
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(
                event(9, 0, EventType.IN, 1), event(13, 0, EventType.OUT, 2),
                event(14, 30, EventType.IN, 3), event(19, 0, EventType.OUT, 4)
            ),
            full,
            now = date.atTime(20, 0)
        )
        assertEquals(510, result.creditedMinutes)
        assertEquals(30, result.balanceMinutes)
    }

    @Test
    fun `open interval is calculated up to now`() {
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(event(9, 0, EventType.IN, 1)),
            full,
            now = date.atTime(15, 0)
        )
        assertTrue(result.isCurrentlyInside)
        assertEquals(300, result.creditedMinutes)
    }

    @Test
    fun `half rate schedule uses four hours and thirty minute lunch`() {
        val half = Schedule(workMinutes = 240, lunchMinutes = 30)
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(event(9, 0, EventType.IN, 1), event(13, 30, EventType.OUT, 2)),
            half,
            now = date.atTime(14, 0)
        )
        assertEquals(240, result.creditedMinutes)
        assertEquals(0, result.balanceMinutes)
    }

    @Test
    fun `september 2026 plan has 22 weekdays and 176 hours`() {
        val result = WorkTimeCalculator.calculateMonth(
            YearMonth.of(2026, 9), emptyList(), full, emptyMap(),
            now = LocalDateTime.of(2026, 9, 1, 0, 0)
        )
        assertEquals(10_560, result.planMinutes)
    }

    @Test
    fun `vacation removes daily norm from monthly plan`() {
        val vacation = mapOf(date to DayOverride(date, DayKind.VACATION))
        val result = WorkTimeCalculator.calculateMonth(
            YearMonth.of(2026, 9), emptyList(), full, vacation,
            now = LocalDateTime.of(2026, 9, 1, 0, 0)
        )
        assertEquals(10_080, result.planMinutes)
    }
}
