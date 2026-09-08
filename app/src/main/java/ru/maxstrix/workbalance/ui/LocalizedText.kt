package ru.maxstrix.workbalance.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.maxstrix.workbalance.R
import ru.maxstrix.workbalance.domain.CalendarRegion
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.ShortenedDayMode

@Composable
fun DayKind.localizedTitle(): String = stringResource(
    when (this) {
        DayKind.AUTO -> R.string.day_kind_auto
        DayKind.WORKDAY -> R.string.day_kind_workday
        DayKind.WEEKEND -> R.string.day_kind_weekend
        DayKind.HOLIDAY -> R.string.day_kind_holiday
        DayKind.VACATION -> R.string.day_kind_vacation
        DayKind.SICK -> R.string.day_kind_sick
        DayKind.BUSINESS_TRIP -> R.string.day_kind_business_trip
        DayKind.DAY_OFF -> R.string.day_kind_day_off
        DayKind.PLANNED_ABSENCE -> R.string.day_kind_planned_absence
    }
)

@Composable
fun CalendarRegion.localizedTitle(): String = stringResource(
    when (this) {
        CalendarRegion.NONE -> R.string.calendar_region_none
        CalendarRegion.SARATOV -> R.string.calendar_region_saratov
    }
)

@Composable
fun ShortenedDayMode.localizedTitle(): String = stringResource(
    when (this) {
        ShortenedDayMode.ASK -> R.string.shortened_mode_ask
        ShortenedDayMode.AUTOMATIC -> R.string.shortened_mode_automatic
        ShortenedDayMode.DISABLED -> R.string.shortened_mode_disabled
    }
)

@Composable
fun localizedWarnings(warnings: List<String>): String {
    val result = mutableListOf<String>()
    for (warning in warnings) {
        result += when {
            warning.startsWith("Два входа подряд: ") -> stringResource(
                R.string.warning_two_entries,
                warning.substringAfter(": ")
            )
            warning.startsWith("Выход без входа: ") -> stringResource(
                R.string.warning_exit_without_entry,
                warning.substringAfter(": ")
            )
            warning == "Нарушен порядок отметок" -> stringResource(R.string.warning_invalid_order)
            else -> warning
        }
    }
    return result.joinToString("\n")
}

@Composable
fun String.localizedCalendarNote(): String {
    val result = mutableListOf<String>()
    for (name in split(" · ")) {
        val resource = calendarNameResource(name)
        result += if (resource == null) name else stringResource(resource)
    }
    return result.joinToString(" · ")
}

@StringRes
private fun calendarNameResource(name: String): Int? = when (name) {
    "Новогодние каникулы" -> R.string.calendar_holiday_new_year
    "Рождество Христово" -> R.string.calendar_holiday_christmas
    "День защитника Отечества" -> R.string.calendar_holiday_defender
    "Международный женский день" -> R.string.calendar_holiday_womens_day
    "Праздник Весны и Труда" -> R.string.calendar_holiday_spring_labour
    "День Победы" -> R.string.calendar_holiday_victory
    "День России" -> R.string.calendar_holiday_russia
    "День народного единства" -> R.string.calendar_holiday_unity
    "Предпраздничный день" -> R.string.calendar_preholiday
    "Радоница" -> R.string.calendar_radonitsa
    "День перед Радоницей" -> R.string.calendar_before_radonitsa
    "Перенос выходного с 3 января" -> R.string.calendar_transfer_jan3
    "Перенос выходного с 4 января" -> R.string.calendar_transfer_jan4
    "Перенос выходного на Международный женский день" -> R.string.calendar_transfer_womens_day
    "Перенос выходного на День Победы" -> R.string.calendar_transfer_victory
    else -> null
}
