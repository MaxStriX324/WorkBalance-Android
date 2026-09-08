package ru.maxstrix.workbalance.data

import org.json.JSONArray
import org.json.JSONObject
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.CalendarRegion
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.MonthResult
import ru.maxstrix.workbalance.domain.ShortenedDayMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class BackupPayload(
    val events: List<WorkEventEntity>,
    val overrides: List<DayOverrideEntity>,
    val settings: List<SettingEntity>
)

enum class BackupValidationError {
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    INVALID_WORK_TARGET,
    INVALID_BREAK_DURATION,
    INVALID_BREAK_REMINDER,
    INVALID_EXIT_REMINDER,
    INVALID_SNOOZE,
    INVALID_SPECIAL_DAY_TARGET
}

class BackupValidationException(val reason: BackupValidationError) :
    IllegalArgumentException(reason.name)

object BackupCodec {
    private const val FORMAT = "ru.maxstrix.workbalance.backup"
    private const val FORMAT_VERSION = 1

    fun encode(data: WorkData): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("settings", JSONObject()
                .put("workplaceName", data.workplaceName)
                .put("workMinutes", data.schedule.workMinutes)
                .put("lunchMinutes", data.schedule.lunchMinutes)
                .put("lunchReminderEnabled", data.lunchReminderEnabled)
                .put("lunchReminderLeadMinutes", data.lunchReminderLeadMinutes)
                .put("forgottenMarkReminderEnabled", data.forgottenMarkReminderSettings.enabled)
                .put("forgottenEntryCheckTime", data.forgottenMarkReminderSettings.entryCheckTime.toString())
                .put("forgottenExitGraceMinutes", data.forgottenMarkReminderSettings.exitGraceMinutes)
                .put("forgottenSnoozeMinutes", data.forgottenMarkReminderSettings.snoozeMinutes)
                .put("automaticUpdateCheckEnabled", data.automaticUpdateCheckEnabled)
                .put("calendarFederalEnabled", data.productionCalendar.settings.federalEnabled)
                .put("calendarRegionalEnabled", data.productionCalendar.settings.regionalEnabled)
                .put("calendarRegion", data.productionCalendar.settings.region.code)
                .put("calendarShortenedMode", data.productionCalendar.settings.shortenedDayMode.name)
            )

        val events = JSONArray()
        data.events.sortedBy { it.at }.forEach { event ->
            events.put(JSONObject()
                .put("at", event.at.toString())
                .put("type", event.type.name)
            )
        }
        root.put("events", events)

        val overrides = JSONArray()
        data.overrides.values.sortedBy { it.date }.forEach { day ->
            val item = JSONObject()
                .put("date", day.date.toString())
                .put("kind", day.kind.name)
            day.customWorkMinutes?.let { item.put("customWorkMinutes", it) }
            overrides.put(item)
        }
        root.put("dayOverrides", overrides)
        return root.toString(2)
    }

    fun parse(json: String, defaultWorkplaceName: String = "Работа"): BackupPayload {
        val root = JSONObject(json)
        validate(root.optString("format") == FORMAT, BackupValidationError.INVALID_FORMAT)
        validate(
            root.optInt("formatVersion", -1) == FORMAT_VERSION,
            BackupValidationError.UNSUPPORTED_VERSION
        )

        val settingsJson = root.getJSONObject("settings")
        val workMinutes = settingsJson.getInt("workMinutes")
        val lunchMinutes = settingsJson.getInt("lunchMinutes")
        val leadMinutes = settingsJson.optInt("lunchReminderLeadMinutes", 15)
        val forgottenEntryTime = runCatching {
            LocalTime.parse(settingsJson.optString("forgottenEntryCheckTime", "10:00"))
        }.getOrDefault(LocalTime.of(10, 0))
        val forgottenExitGrace = settingsJson.optInt("forgottenExitGraceMinutes", 60)
        val forgottenSnooze = settingsJson.optInt("forgottenSnoozeMinutes", 30)
        val calendarRegion = CalendarRegion.fromCode(
            settingsJson.optString("calendarRegion", CalendarRegion.SARATOV.code)
        )
        val shortenedMode = runCatching {
            ShortenedDayMode.valueOf(settingsJson.optString("calendarShortenedMode", ShortenedDayMode.ASK.name))
        }.getOrDefault(ShortenedDayMode.ASK)
        validate(workMinutes in 1..1440, BackupValidationError.INVALID_WORK_TARGET)
        validate(lunchMinutes in 0..720, BackupValidationError.INVALID_BREAK_DURATION)
        validate(leadMinutes in 1..59, BackupValidationError.INVALID_BREAK_REMINDER)
        validate(forgottenExitGrace in 0..240, BackupValidationError.INVALID_EXIT_REMINDER)
        validate(forgottenSnooze in 5..240, BackupValidationError.INVALID_SNOOZE)
        val settings = listOf(
            SettingEntity(WorkRepository.KEY_WORK_MINUTES, workMinutes.toString()),
            SettingEntity(WorkRepository.KEY_LUNCH_MINUTES, lunchMinutes.toString()),
            SettingEntity(
                WorkRepository.KEY_WORKPLACE_NAME,
                settingsJson.optString("workplaceName", defaultWorkplaceName)
            ),
            SettingEntity(WorkRepository.KEY_LUNCH_REMINDER_ENABLED, settingsJson.optBoolean("lunchReminderEnabled", true).toString()),
            SettingEntity(WorkRepository.KEY_LUNCH_REMINDER_LEAD, leadMinutes.toString()),
            SettingEntity(
                WorkRepository.KEY_FORGOTTEN_MARK_ENABLED,
                settingsJson.optBoolean("forgottenMarkReminderEnabled", false).toString()
            ),
            SettingEntity(WorkRepository.KEY_FORGOTTEN_ENTRY_TIME, forgottenEntryTime.toString()),
            SettingEntity(WorkRepository.KEY_FORGOTTEN_EXIT_GRACE, forgottenExitGrace.toString()),
            SettingEntity(WorkRepository.KEY_FORGOTTEN_SNOOZE, forgottenSnooze.toString()),
            SettingEntity(
                WorkRepository.KEY_AUTOMATIC_UPDATE_CHECK,
                settingsJson.optBoolean("automaticUpdateCheckEnabled", true).toString()
            ),
            SettingEntity(
                WorkRepository.KEY_CALENDAR_FEDERAL_ENABLED,
                settingsJson.optBoolean("calendarFederalEnabled", true).toString()
            ),
            SettingEntity(
                WorkRepository.KEY_CALENDAR_REGIONAL_ENABLED,
                settingsJson.optBoolean("calendarRegionalEnabled", false).toString()
            ),
            SettingEntity(WorkRepository.KEY_CALENDAR_REGION, calendarRegion.code),
            SettingEntity(WorkRepository.KEY_CALENDAR_SHORTENED_MODE, shortenedMode.name)
        )

        val eventsJson = root.getJSONArray("events")
        val events = buildList {
            for (index in 0 until eventsJson.length()) {
                val item = eventsJson.getJSONObject(index)
                val at = LocalDateTime.parse(item.getString("at"))
                val type = EventType.valueOf(item.getString("type"))
                add(WorkEventEntity(localDateTime = at.toString(), type = type.name))
            }
        }.sortedBy { it.localDateTime }

        val overridesJson = root.optJSONArray("dayOverrides") ?: JSONArray()
        val overrides = buildList {
            for (index in 0 until overridesJson.length()) {
                val item = overridesJson.getJSONObject(index)
                val date = LocalDate.parse(item.getString("date"))
                val kind = DayKind.valueOf(item.getString("kind"))
                val custom = if (item.has("customWorkMinutes")) item.getInt("customWorkMinutes") else null
                if (custom != null) {
                    validate(custom in 0..1440, BackupValidationError.INVALID_SPECIAL_DAY_TARGET)
                }
                add(DayOverrideEntity(date.toEpochDay(), kind.name, custom))
            }
        }
        return BackupPayload(events, overrides, settings)
    }

    fun monthCsv(month: MonthResult, languageTag: String = "ru"): String {
        val locale = if (languageTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(languageTag)
        val isRussian = locale.language.equals("ru", ignoreCase = true)
        val header = if (isRussian) {
            listOf(
                "Дата", "День недели", "Норма", "Первый вход", "Последний выход",
                "Отметки", "На территории", "Вне территории всего", "Обед вне территории",
                "Обед на территории", "Дополнительное отсутствие",
                "Зачтено", "Баланс", "Производственный календарь", "Предупреждения"
            )
        } else {
            listOf(
                "Date", "Weekday", "Target", "First check-in", "Last check-out",
                "Records", "On-site time", "Off-site total", "Official break off-site",
                "Official break on-site", "Additional absence",
                "Credited", "Balance", "Production calendar", "Warnings"
            )
        }
        val rows = month.days.map { day ->
            listOf(
                day.date.toString(),
                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                duration(day.requiredMinutes),
                day.firstIn?.toLocalTime()?.toString()?.take(5).orEmpty(),
                day.lastOut?.toLocalTime()?.toString()?.take(5).orEmpty(),
                day.events.joinToString(" | ") {
                    val type = when {
                        isRussian && it.type == EventType.IN -> "вход"
                        isRussian -> "выход"
                        it.type == EventType.IN -> "check-in"
                        else -> "check-out"
                    }
                    "${it.at.toLocalTime().toString().take(5)} $type"
                },
                duration(day.presenceMinutes),
                duration(day.outsideMinutes),
                duration(day.lunchOutsideMinutes),
                duration(day.deductedLunchMinutes),
                duration(day.extraOutsideMinutes),
                duration(day.creditedMinutes),
                signedDuration(day.balanceMinutes),
                localizeCalendarNote(day.calendarNote.orEmpty(), isRussian),
                day.warnings.joinToString(" | ") { localizeWarning(it, isRussian) }
            )
        }
        return buildString {
            append('\uFEFF')
            appendLine(header.joinToString(";") { csvCell(it) })
            rows.forEach { row -> appendLine(row.joinToString(";") { csvCell(it) }) }
        }
    }

    private fun duration(minutes: Long): String = "%d:%02d".format(minutes / 60, minutes % 60)

    private fun signedDuration(minutes: Long): String {
        val sign = when { minutes > 0 -> "+"; minutes < 0 -> "-"; else -> "" }
        return sign + duration(abs(minutes))
    }

    private fun localizeWarning(warning: String, isRussian: Boolean): String {
        if (isRussian) return warning
        return when {
            warning.startsWith("Два входа подряд: ") ->
                "Two consecutive check-ins: ${warning.substringAfter(": ")}"
            warning.startsWith("Выход без входа: ") ->
                "Check-out without check-in: ${warning.substringAfter(": ")}"
            warning == "Нарушен порядок отметок" -> "The record order is invalid"
            else -> warning
        }
    }

    private fun localizeCalendarNote(note: String, isRussian: Boolean): String {
        if (isRussian || note.isBlank()) return note
        val translations = mapOf(
            "Новогодние каникулы" to "New Year holidays",
            "Рождество Христово" to "Orthodox Christmas Day",
            "День защитника Отечества" to "Defender of the Fatherland Day",
            "Международный женский день" to "International Women's Day",
            "Праздник Весны и Труда" to "Spring and Labour Day",
            "День Победы" to "Victory Day",
            "День России" to "Russia Day",
            "День народного единства" to "National Unity Day",
            "Предпраздничный день" to "Pre-holiday workday",
            "Радоница" to "Radonitsa",
            "День перед Радоницей" to "Day before Radonitsa",
            "Перенос выходного с 3 января" to "Day off transferred from January 3",
            "Перенос выходного с 4 января" to "Day off transferred from January 4",
            "Перенос выходного на Международный женский день" to
                "Transferred day off for International Women's Day",
            "Перенос выходного на День Победы" to "Transferred day off for Victory Day"
        )
        return note.split(" · ").joinToString(" · ") { translations[it] ?: it }
    }

    private fun validate(condition: Boolean, reason: BackupValidationError) {
        if (!condition) throw BackupValidationException(reason)
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
