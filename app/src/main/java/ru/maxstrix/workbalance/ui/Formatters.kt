package ru.maxstrix.workbalance.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val ru = Locale("ru")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)

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
fun LocalDate.asDate(): String = format(dateFormatter)
fun YearMonth.asMonthTitle(): String = month.getDisplayName(TextStyle.FULL_STANDALONE, ru)
    .replaceFirstChar { it.uppercase(ru) } + " $year"
