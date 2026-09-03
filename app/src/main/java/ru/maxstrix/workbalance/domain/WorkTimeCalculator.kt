package ru.maxstrix.workbalance.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.max

object WorkTimeCalculator {

    fun requiredMinutes(
        date: LocalDate,
        schedule: Schedule,
        override: DayOverride?
    ): Long {
        if (override?.customWorkMinutes != null) return override.customWorkMinutes.toLong()
        return when (override?.kind ?: DayKind.AUTO) {
            DayKind.WORKDAY -> schedule.workMinutes.toLong()
            DayKind.BUSINESS_TRIP -> schedule.workMinutes.toLong()
            DayKind.PLANNED_ABSENCE -> schedule.workMinutes.toLong()
            DayKind.AUTO -> if (date.dayOfWeek in schedule.workingDays) schedule.workMinutes.toLong() else 0
            DayKind.WEEKEND, DayKind.HOLIDAY, DayKind.VACATION,
            DayKind.SICK, DayKind.DAY_OFF -> 0
        }
    }

    fun calculateDay(
        date: LocalDate,
        rawEvents: List<WorkEvent>,
        schedule: Schedule,
        override: DayOverride? = null,
        now: LocalDateTime = LocalDateTime.now()
    ): DayResult {
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()
        val effectiveNow = when {
            now < dayStart -> dayStart
            now >= dayEnd -> dayEnd
            else -> now
        }
        val events = rawEvents
            .filter { !it.at.isBefore(dayStart) && it.at.isBefore(dayEnd) }
            .sortedWith(compareBy<WorkEvent> { it.at }.thenBy { it.id })

        var insideSince: LocalDateTime? = null
        var presence = 0L
        val warnings = mutableListOf<String>()
        var firstIn: LocalDateTime? = null
        var lastOut: LocalDateTime? = null

        events.forEach { event ->
            when (event.type) {
                EventType.IN -> {
                    if (insideSince != null) {
                        warnings += "Два входа подряд: ${event.at.toLocalTime()}"
                    } else {
                        insideSince = event.at
                        if (firstIn == null) firstIn = event.at
                    }
                }
                EventType.OUT -> {
                    val start = insideSince
                    if (start == null) {
                        warnings += "Выход без входа: ${event.at.toLocalTime()}"
                    } else if (event.at.isBefore(start)) {
                        warnings += "Нарушен порядок отметок"
                    } else {
                        presence += Duration.between(start, event.at).toMinutes()
                        insideSince = null
                        lastOut = event.at
                    }
                }
            }
        }

        if (insideSince != null && effectiveNow.isAfter(insideSince)) {
            presence += Duration.between(insideSince, effectiveNow).toMinutes()
        }

        val spanEnd = when {
            insideSince != null -> effectiveNow
            lastOut != null -> lastOut
            else -> firstIn
        }
        val totalSpan = if (firstIn != null && spanEnd != null && !spanEnd.isBefore(firstIn)) {
            Duration.between(firstIn, spanEnd).toMinutes()
        } else 0L
        val outside = max(0, totalSpan - presence)
        val lunchRequired = schedule.lunchMinutes.toLong()
        val lunchOutside = minOf(outside, lunchRequired)
        val extraOutside = max(0, outside - lunchRequired)
        val missingLunch = if (firstIn == null) 0 else max(0, lunchRequired - outside)
        val credited = max(0, presence - missingLunch)
        val required = requiredMinutes(date, schedule, override)

        return DayResult(
            date = date,
            events = events,
            firstIn = firstIn,
            lastOut = lastOut,
            presenceMinutes = presence,
            outsideMinutes = outside,
            lunchOutsideMinutes = lunchOutside,
            extraOutsideMinutes = extraOutside,
            deductedLunchMinutes = missingLunch,
            creditedMinutes = credited,
            requiredMinutes = required,
            balanceMinutes = credited - required,
            isCurrentlyInside = insideSince != null,
            warnings = warnings
        )
    }

    fun calculateMonth(
        month: YearMonth,
        events: List<WorkEvent>,
        schedule: Schedule,
        overrides: Map<LocalDate, DayOverride>,
        now: LocalDateTime = LocalDateTime.now()
    ): MonthResult {
        val today = now.toLocalDate()
        val days = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            calculateDay(date, events, schedule, overrides[date], now)
        }
        val plan = days.sumOf { it.requiredMinutes }
        val credited = days.sumOf { it.creditedMinutes }
        val plannedThroughToday = days
            .filter { !it.date.isAfter(today) }
            .sumOf { it.requiredMinutes }
        val creditedThroughToday = days
            .filter { !it.date.isAfter(today) }
            .sumOf { it.creditedMinutes }
        val remainingDays = days.count {
            it.requiredMinutes > 0 &&
                !it.date.isBefore(today) &&
                overrides[it.date]?.kind != DayKind.PLANNED_ABSENCE
        }
        val remaining = max(0, plan - credited)
        val workedDays = days.count { it.presenceMinutes > 0 }

        return MonthResult(
            month = month,
            planMinutes = plan,
            creditedMinutes = credited,
            balanceToDateMinutes = creditedThroughToday - plannedThroughToday,
            remainingMinutes = remaining,
            remainingWorkDays = remainingDays,
            averageMinutesPerRemainingDay = if (remainingDays == 0) 0 else (remaining + remainingDays - 1) / remainingDays,
            presenceMinutes = days.sumOf { it.presenceMinutes },
            outsideMinutes = days.sumOf { it.outsideMinutes },
            lunchOutsideMinutes = days.sumOf { it.lunchOutsideMinutes },
            extraOutsideMinutes = days.sumOf { it.extraOutsideMinutes },
            deductedLunchMinutes = days.sumOf { it.deductedLunchMinutes },
            workedDays = workedDays,
            averageCreditedPerWorkedDay = if (workedDays == 0) 0 else credited / workedDays,
            days = days
        )
    }

    fun minutesUntilCreditedTarget(day: DayResult, targetMinutes: Long): Long {
        if (targetMinutes <= day.creditedMinutes) return 0
        return max(0, targetMinutes + day.deductedLunchMinutes - day.presenceMinutes)
    }

    fun shouldScheduleLunchReminder(
        day: DayResult,
        enabled: Boolean,
        leadMinutes: Int
    ): Boolean {
        val remainingLunch = day.deductedLunchMinutes
        return enabled &&
            day.creditedMinutes < day.requiredMinutes &&
            remainingLunch > leadMinutes
    }

    fun defaultScheduleForRate(rate: Double): Schedule = when {
        rate <= 0.5 -> Schedule(workMinutes = 4 * 60, lunchMinutes = 30)
        else -> Schedule(workMinutes = 8 * 60, lunchMinutes = 60)
    }
}
