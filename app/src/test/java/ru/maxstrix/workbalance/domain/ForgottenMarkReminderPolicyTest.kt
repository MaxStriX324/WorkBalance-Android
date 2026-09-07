package ru.maxstrix.workbalance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForgottenMarkReminderPolicyTest {
    private val date = LocalDate.of(2026, 9, 7)
    private val schedule = Schedule(workMinutes = 480, lunchMinutes = 60)

    @Test
    fun `entry reminder is shown on workday without entry`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            emptyList(),
            schedule,
            now = date.atTime(10, 0)
        )

        assertTrue(ForgottenMarkReminderPolicy.shouldRemindEntry(day, null))
    }

    @Test
    fun `entry reminder is suppressed after any entry`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            listOf(WorkEvent(1, date.atTime(8, 45), EventType.IN)),
            schedule,
            now = date.atTime(10, 0)
        )

        assertFalse(ForgottenMarkReminderPolicy.shouldRemindEntry(day, null))
    }

    @Test
    fun `entry reminder skips planned absence and business trip`() {
        listOf(DayKind.PLANNED_ABSENCE, DayKind.BUSINESS_TRIP).forEach { kind ->
            val override = DayOverride(date, kind)
            val day = WorkTimeCalculator.calculateDay(
                date,
                emptyList(),
                schedule,
                override = override,
                now = date.atTime(10, 0)
            )

            assertFalse(ForgottenMarkReminderPolicy.shouldRemindEntry(day, override))
        }
    }

    @Test
    fun `entry reminder skips non working day`() {
        val weekend = LocalDate.of(2026, 9, 6)
        val day = WorkTimeCalculator.calculateDay(
            weekend,
            emptyList(),
            schedule,
            now = weekend.atTime(10, 0)
        )

        assertFalse(ForgottenMarkReminderPolicy.shouldRemindEntry(day, null))
    }

    @Test
    fun `exit reminder delay includes remaining work lunch and grace`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            listOf(WorkEvent(1, date.atTime(9, 0), EventType.IN)),
            schedule,
            now = date.atTime(10, 0)
        )

        assertTrue(ForgottenMarkReminderPolicy.shouldRemindExit(day))
        assertEquals(540, ForgottenMarkReminderPolicy.exitDelayMinutes(day, 60))
    }

    @Test
    fun `exit reminder is suppressed after exit`() {
        val day = WorkTimeCalculator.calculateDay(
            date,
            listOf(
                WorkEvent(1, date.atTime(9, 0), EventType.IN),
                WorkEvent(2, date.atTime(18, 0), EventType.OUT)
            ),
            schedule,
            now = date.atTime(19, 0)
        )

        assertFalse(ForgottenMarkReminderPolicy.shouldRemindExit(day))
    }
}
