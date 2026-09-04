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

    private fun productionCalendar(
        shortenedMode: ShortenedDayMode = ShortenedDayMode.AUTOMATIC,
        includeSaratov: Boolean = false
    ): ProductionCalendar {
        val federalDaysOff = listOf(
            "2026-01-01", "2026-01-02", "2026-01-05", "2026-01-06", "2026-01-07",
            "2026-01-08", "2026-01-09", "2026-02-23", "2026-03-09", "2026-05-01",
            "2026-05-11", "2026-06-12", "2026-11-04", "2026-12-31"
        ).map { CalendarRule(LocalDate.parse(it), CalendarDayType.DAY_OFF, "Праздник") }
        val federalShortened = listOf("2026-04-30", "2026-05-08", "2026-06-11", "2026-11-03")
            .map { CalendarRule(LocalDate.parse(it), CalendarDayType.SHORTENED, "Сокращённый день", 60) }
        val federal = CalendarPack(
            id = "RU-2026-test",
            year = 2026,
            regionCode = null,
            title = "Россия, 2026",
            revision = "test",
            official = true,
            source = "test",
            rules = federalDaysOff + federalShortened
        )
        val saratov = CalendarPack(
            id = "RU-SAR-2026-test",
            year = 2026,
            regionCode = CalendarRegion.SARATOV.code,
            title = "Саратовская область, 2026",
            revision = "test",
            official = true,
            source = "test",
            rules = listOf(
                CalendarRule(LocalDate.of(2026, 4, 20), CalendarDayType.SHORTENED, "Перед Радоницей", 60),
                CalendarRule(LocalDate.of(2026, 4, 21), CalendarDayType.DAY_OFF, "Радоница")
            )
        )
        return ProductionCalendar(
            settings = ProductionCalendarSettings(
                federalEnabled = true,
                regionalEnabled = includeSaratov,
                region = CalendarRegion.SARATOV,
                shortenedDayMode = shortenedMode
            ),
            packs = if (includeSaratov) listOf(federal, saratov) else listOf(federal)
        )
    }

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

    @Test
    fun `federal calendar calculates official 2026 annual norm`() {
        val calendar = productionCalendar()
        val annualMinutes = (1..12).sumOf { month ->
            WorkTimeCalculator.calculateMonth(
                YearMonth.of(2026, month), emptyList(), full, emptyMap(),
                now = LocalDateTime.of(2026, 1, 1, 0, 0),
                productionCalendar = calendar
            ).planMinutes
        }

        assertEquals(1_972L * 60L, annualMinutes)
    }

    @Test
    fun `saratov calendar adds radonitsa and calculates regional April norm`() {
        val calendar = productionCalendar(includeSaratov = true)
        val result = WorkTimeCalculator.calculateMonth(
            YearMonth.of(2026, 4), emptyList(), full, emptyMap(),
            now = LocalDateTime.of(2026, 4, 1, 0, 0),
            productionCalendar = calendar
        )
        val annualMinutes = (1..12).sumOf { month ->
            WorkTimeCalculator.calculateMonth(
                YearMonth.of(2026, month), emptyList(), full, emptyMap(),
                now = LocalDateTime.of(2026, 1, 1, 0, 0),
                productionCalendar = calendar
            ).planMinutes
        }

        assertEquals(166L * 60L, result.planMinutes)
        assertEquals(1_963L * 60L, annualMinutes)
        assertEquals(0L, result.days.first { it.date == LocalDate.of(2026, 4, 21) }.requiredMinutes)
    }

    @Test
    fun `ask mode keeps full norm until shortened day decision is made`() {
        val shortenedDate = LocalDate.of(2026, 4, 30)
        val calendar = productionCalendar(shortenedMode = ShortenedDayMode.ASK)
        val undecided = WorkTimeCalculator.calculateDay(
            shortenedDate, emptyList(), full,
            now = shortenedDate.atStartOfDay(), productionCalendar = calendar
        )
        val accepted = WorkTimeCalculator.calculateDay(
            shortenedDate, emptyList(), full,
            override = DayOverride(shortenedDate, DayKind.AUTO, 420),
            now = shortenedDate.atStartOfDay(), productionCalendar = calendar
        )

        assertEquals(480L, undecided.requiredMinutes)
        assertTrue(undecided.shortenedDecisionNeeded)
        assertEquals(420L, accepted.requiredMinutes)
        assertTrue(accepted.shortenedApplied)
    }

    @Test
    fun `manual workday has priority over regional holiday`() {
        val radonitsa = LocalDate.of(2026, 4, 21)
        val required = WorkTimeCalculator.requiredMinutes(
            radonitsa,
            full,
            DayOverride(radonitsa, DayKind.WORKDAY),
            productionCalendar(includeSaratov = true)
        )

        assertEquals(480L, required)
    }

    @Test
    fun `missing future calendar uses weekly schedule and reports missing year`() {
        val calendar = productionCalendar()
        val monday = LocalDate.of(2027, 1, 4)

        assertTrue(!calendar.hasYear(2027))
        assertEquals(480L, WorkTimeCalculator.requiredMinutes(monday, full, null, calendar))
    }
}
