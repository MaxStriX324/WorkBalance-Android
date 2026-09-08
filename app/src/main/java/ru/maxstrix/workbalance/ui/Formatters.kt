package ru.maxstrix.workbalance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Long.asDuration(): String {
    val value = abs(this)
    val hours = value / 60
    val minutes = value % 60
    return "%d:%02d".format(hours, minutes)
}

fun Long.asSignedDuration(): String = when {
    this > 0 -> "+${asDuration()}"
    this < 0 -> "−${asDuration()}"
    else -> "0:00"
}

fun LocalDateTime.asTime(): String = format(timeFormatter)

@Composable
fun currentAppLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun LocalDate.asDate(): String = format(DateTimeFormatter.ofPattern("d MMMM", currentAppLocale()))

@Composable
fun LocalDate.asWeekday(style: TextStyle): String =
    dayOfWeek.getDisplayName(style, currentAppLocale())

@Composable
fun YearMonth.asMonthTitle(): String {
    val locale = currentAppLocale()
    return month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) } + " $year"
}
