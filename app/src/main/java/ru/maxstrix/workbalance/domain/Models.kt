package ru.maxstrix.workbalance.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class EventType { IN, OUT }

enum class DayKind(val title: String) {
    AUTO("По календарю"),
    WORKDAY("Рабочий день"),
    WEEKEND("Выходной"),
    HOLIDAY("Праздник"),
    VACATION("Отпуск"),
    SICK("Больничный"),
    BUSINESS_TRIP("Командировка"),
    DAY_OFF("Отгул")
}

data class WorkEvent(
    val id: Long = 0,
    val at: LocalDateTime,
    val type: EventType
)

data class Schedule(
    val workMinutes: Int = 8 * 60,
    val lunchMinutes: Int = 60,
    val workingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )
)

data class DayOverride(
    val date: LocalDate,
    val kind: DayKind,
    val customWorkMinutes: Int? = null
)

data class DayResult(
    val date: LocalDate,
    val events: List<WorkEvent>,
    val firstIn: LocalDateTime?,
    val lastOut: LocalDateTime?,
    val presenceMinutes: Long,
    val outsideMinutes: Long,
    val deductedLunchMinutes: Long,
    val creditedMinutes: Long,
    val requiredMinutes: Long,
    val balanceMinutes: Long,
    val isCurrentlyInside: Boolean,
    val warnings: List<String>
)

data class MonthResult(
    val month: YearMonth,
    val planMinutes: Long,
    val creditedMinutes: Long,
    val balanceToDateMinutes: Long,
    val remainingMinutes: Long,
    val remainingWorkDays: Int,
    val averageMinutesPerRemainingDay: Long,
    val days: List<DayResult>
)
