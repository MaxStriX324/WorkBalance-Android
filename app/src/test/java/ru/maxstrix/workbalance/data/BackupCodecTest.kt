package ru.maxstrix.workbalance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.DayOverride
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.Schedule
import ru.maxstrix.workbalance.domain.WorkEvent
import ru.maxstrix.workbalance.domain.WorkTimeCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class BackupCodecTest {
    @Test
    fun `backup round trip keeps events overrides and settings`() {
        val date = LocalDate.of(2026, 9, 2)
        val data = WorkData(
            events = listOf(
                WorkEvent(1, date.atTime(8, 45), EventType.IN),
                WorkEvent(2, date.atTime(13, 0), EventType.OUT)
            ),
            overrides = mapOf(date to DayOverride(date, DayKind.WORKDAY)),
            schedule = Schedule(480, 60),
            workplaceName = "Работа",
            lunchReminderEnabled = true,
            lunchReminderLeadMinutes = 15,
            automaticUpdateCheckEnabled = false
        )

        val parsed = BackupCodec.parse(BackupCodec.encode(data))

        assertEquals(2, parsed.events.size)
        assertEquals("2026-09-02T08:45", parsed.events.first().localDateTime)
        assertEquals("IN", parsed.events.first().type)
        assertEquals(1, parsed.overrides.size)
        assertEquals("WORKDAY", parsed.overrides.first().kind)
        assertTrue(parsed.settings.any { it.key == WorkRepository.KEY_WORK_MINUTES && it.value == "480" })
        assertTrue(parsed.settings.any {
            it.key == WorkRepository.KEY_AUTOMATIC_UPDATE_CHECK && it.value == "false"
        })
    }

    @Test
    fun `month csv contains exact daily details`() {
        val date = LocalDate.of(2026, 9, 2)
        val events = listOf(
            WorkEvent(1, date.atTime(9, 0), EventType.IN),
            WorkEvent(2, date.atTime(18, 0), EventType.OUT)
        )
        val month = WorkTimeCalculator.calculateMonth(
            YearMonth.of(2026, 9), events, Schedule(480, 60), emptyMap(),
            LocalDateTime.of(2026, 9, 2, 19, 0)
        )

        val csv = BackupCodec.monthCsv(month)

        assertTrue(csv.startsWith('\uFEFF'))
        assertTrue(csv.contains("\"2026-09-02\""))
        assertTrue(csv.contains("\"09:00 вход | 18:00 выход\""))
        assertTrue(csv.contains("\"8:00\""))
    }
}
