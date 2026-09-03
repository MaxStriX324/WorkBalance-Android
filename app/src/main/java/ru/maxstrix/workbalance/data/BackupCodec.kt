package ru.maxstrix.workbalance.data

import org.json.JSONArray
import org.json.JSONObject
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.MonthResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class BackupPayload(
    val events: List<WorkEventEntity>,
    val overrides: List<DayOverrideEntity>,
    val settings: List<SettingEntity>
)

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
                .put("automaticUpdateCheckEnabled", data.automaticUpdateCheckEnabled)
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

    fun parse(json: String): BackupPayload {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Это не резервная копия WorkBalance" }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "Неподдерживаемая версия резервной копии" }

        val settingsJson = root.getJSONObject("settings")
        val workMinutes = settingsJson.getInt("workMinutes")
        val lunchMinutes = settingsJson.getInt("lunchMinutes")
        val leadMinutes = settingsJson.optInt("lunchReminderLeadMinutes", 15)
        require(workMinutes in 1..1440) { "Некорректная дневная норма" }
        require(lunchMinutes in 0..720) { "Некорректная длительность обеда" }
        require(leadMinutes in 1..59) { "Некорректное время напоминания" }
        val settings = listOf(
            SettingEntity(WorkRepository.KEY_WORK_MINUTES, workMinutes.toString()),
            SettingEntity(WorkRepository.KEY_LUNCH_MINUTES, lunchMinutes.toString()),
            SettingEntity(WorkRepository.KEY_WORKPLACE_NAME, settingsJson.optString("workplaceName", "Работа")),
            SettingEntity(WorkRepository.KEY_LUNCH_REMINDER_ENABLED, settingsJson.optBoolean("lunchReminderEnabled", true).toString()),
            SettingEntity(WorkRepository.KEY_LUNCH_REMINDER_LEAD, leadMinutes.toString()),
            SettingEntity(
                WorkRepository.KEY_AUTOMATIC_UPDATE_CHECK,
                settingsJson.optBoolean("automaticUpdateCheckEnabled", true).toString()
            )
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
                if (custom != null) require(custom in 0..1440) { "Некорректная норма особого дня" }
                add(DayOverrideEntity(date.toEpochDay(), kind.name, custom))
            }
        }
        return BackupPayload(events, overrides, settings)
    }

    fun monthCsv(month: MonthResult): String {
        val locale = Locale("ru")
        val header = listOf(
            "Дата", "День недели", "Норма", "Первый вход", "Последний выход",
            "Отметки", "На территории", "Вне территории всего", "Обед вне территории",
            "Обед на территории", "Дополнительное отсутствие",
            "Зачтено", "Баланс", "Предупреждения"
        )
        val rows = month.days.map { day ->
            listOf(
                day.date.toString(),
                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                duration(day.requiredMinutes),
                day.firstIn?.toLocalTime()?.toString()?.take(5).orEmpty(),
                day.lastOut?.toLocalTime()?.toString()?.take(5).orEmpty(),
                day.events.joinToString(" | ") { "${it.at.toLocalTime().toString().take(5)} ${if (it.type == EventType.IN) "вход" else "выход"}" },
                duration(day.presenceMinutes),
                duration(day.outsideMinutes),
                duration(day.lunchOutsideMinutes),
                duration(day.deductedLunchMinutes),
                duration(day.extraOutsideMinutes),
                duration(day.creditedMinutes),
                signedDuration(day.balanceMinutes),
                day.warnings.joinToString(" | ")
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

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
