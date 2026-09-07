package ru.maxstrix.workbalance.domain

object ForgottenMarkReminderPolicy {
    fun shouldRemindEntry(day: DayResult, override: DayOverride?): Boolean =
        isAvailableWorkDay(day.requiredMinutes, override) &&
            day.events.none { it.type == EventType.IN }

    fun shouldRemindExit(day: DayResult): Boolean = day.isCurrentlyInside

    fun exitDelayMinutes(day: DayResult, graceMinutes: Int): Long =
        WorkTimeCalculator.minutesUntilCreditedTarget(day, day.requiredMinutes) +
            graceMinutes.coerceAtLeast(0)

    fun isAvailableWorkDay(requiredMinutes: Long, override: DayOverride?): Boolean =
        requiredMinutes > 0 && override?.kind !in setOf(
            DayKind.PLANNED_ABSENCE,
            DayKind.BUSINESS_TRIP
        )
}
