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
        assertEquals(60, result.lunchOutsideMinutes)
        assertEquals(30, result.extraOutsideMinutes)
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

    @Test
    fun `exit forecast includes uncovered lunch during first hour`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            listOf(event(9, 0, EventType.IN, 1)),
            full,
            now = date.atTime(9, 30)
        )

        assertEquals(510, WorkTimeCalculator.minutesUntilCreditedTarget(day, 480))
    }

    @Test
    fun `short remaining lunch does not create reminder`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            listOf(
                event(9, 0, EventType.IN, 1),
                event(13, 0, EventType.OUT, 2),
                event(13, 51, EventType.IN, 3)
            ),
            full,
            now = date.atTime(17, 0)
        )

        assertEquals(9, day.deductedLunchMinutes)
        assertTrue(!WorkTimeCalculator.shouldScheduleLunchReminder(day, true, 15))
    }

    @Test
    fun `completed shift does not create lunch reminder on final exit`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            listOf(event(9, 0, EventType.IN, 1)),
            full,
            now = date.atTime(18, 0)
        )

        assertEquals(480, day.creditedMinutes)
        assertTrue(!WorkTimeCalculator.shouldScheduleLunchReminder(day, true, 15))
    }

    @Test
    fun `planned absence keeps plan but is excluded from available days`() {
        val absenceDate = LocalDate.of(2026, 9, 3)
        val result = WorkTimeCalculator.calculateMonth(
            YearMonth.of(2026, 9),
            emptyList(),
            full,
            mapOf(absenceDate to DayOverride(absenceDate, DayKind.PLANNED_ABSENCE)),
            now = LocalDateTime.of(2026, 9, 1, 0, 0)
        )

        assertEquals(10_560, result.planMinutes)
        assertEquals(21, result.remainingWorkDays)
    }

    @Test
    fun `partial current day does not make daily average artificially lower`() {
        val events = listOf(
            WorkEvent(1, LocalDateTime.of(2026, 9, 1, 9, 0), EventType.IN),
            WorkEvent(2, LocalDateTime.of(2026, 9, 1, 18, 0), EventType.OUT),
            WorkEvent(3, LocalDateTime.of(2026, 9, 2, 9, 0), EventType.IN),
            WorkEvent(4, LocalDateTime.of(2026, 9, 2, 18, 0), EventType.OUT),
            WorkEvent(5, LocalDateTime.of(2026, 9, 3, 9, 0), EventType.IN),
            WorkEvent(6, LocalDateTime.of(2026, 9, 3, 15, 58), EventType.OUT),
            WorkEvent(7, LocalDateTime.of(2026, 9, 4, 8, 44), EventType.IN),
            WorkEvent(8, LocalDateTime.of(2026, 9, 4, 10, 0), EventType.OUT),
            WorkEvent(9, LocalDateTime.of(2026, 9, 4, 10, 5), EventType.IN)
        )

        val result = WorkTimeCalculator.calculateMonth(
            YearMonth.of(2026, 9),
            events,
            full,
            emptyMap(),
            now = LocalDateTime.of(2026, 9, 4, 12, 22)
        )

        assertEquals(9_084, result.remainingMinutes)
        assertEquals(19, result.remainingWorkDays)
        assertEquals(487, result.averageMinutesPerRemainingDay)
    }

    @Test
    fun `zero lunch schedule has no lunch components`() {
        val withoutLunch = Schedule(workMinutes = 480, lunchMinutes = 0)
        val result = WorkTimeCalculator.calculateDay(
            date,
            listOf(event(9, 0, EventType.IN, 1)),
            withoutLunch,
            now = date.atTime(11, 0)
        )

        assertEquals(120, result.creditedMinutes)
        assertEquals(0, result.lunchOutsideMinutes)
        assertEquals(0, result.deductedLunchMinutes)
    }
}
