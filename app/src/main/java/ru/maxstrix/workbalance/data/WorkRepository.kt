package ru.maxstrix.workbalance.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.DayOverride
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.Schedule
import ru.maxstrix.workbalance.domain.WorkEvent
import java.time.LocalDate
import java.time.LocalDateTime

data class WorkData(
    val events: List<WorkEvent>,
    val overrides: Map<LocalDate, DayOverride>,
    val schedule: Schedule,
    val workplaceName: String,
    val lunchReminderEnabled: Boolean,
    val lunchReminderLeadMinutes: Int
)

class WorkRepository(private val dao: WorkDao) {
    val data: Flow<WorkData> = combine(
        dao.observeEvents(), dao.observeOverrides(), dao.observeSettings()
    ) { eventRows, overrideRows, settingsRows ->
        val settings = settingsRows.associate { it.key to it.value }
        WorkData(
            events = eventRows.map { it.toDomain() },
            overrides = overrideRows.associate { row ->
                row.date to DayOverride(row.date, row.dayKind, row.customWorkMinutes)
            },
            schedule = Schedule(
                workMinutes = settings[KEY_WORK_MINUTES]?.toIntOrNull() ?: 480,
                lunchMinutes = settings[KEY_LUNCH_MINUTES]?.toIntOrNull() ?: 60
            ),
            workplaceName = settings[KEY_WORKPLACE_NAME] ?: "Работа",
            lunchReminderEnabled = settings[KEY_LUNCH_REMINDER_ENABLED]?.toBooleanStrictOrNull() ?: true,
            lunchReminderLeadMinutes = settings[KEY_LUNCH_REMINDER_LEAD]?.toIntOrNull() ?: 15
        )
    }

    suspend fun addEvent(at: LocalDateTime, type: EventType) {
        dao.insertEvent(WorkEventEntity(localDateTime = at.toString(), type = type.name))
    }

    suspend fun updateEvent(event: WorkEvent) {
        dao.updateEvent(WorkEventEntity(event.id, event.at.toString(), event.type.name))
    }

    suspend fun deleteEvent(event: WorkEvent) {
        dao.deleteEvent(WorkEventEntity(event.id, event.at.toString(), event.type.name))
    }

    suspend fun setDay(date: LocalDate, kind: DayKind, customMinutes: Int? = null) {
        if (kind == DayKind.AUTO && customMinutes == null) dao.deleteOverride(date.toEpochDay())
        else dao.upsertOverride(DayOverrideEntity(date.toEpochDay(), kind.name, customMinutes))
    }

    suspend fun setSchedule(workMinutes: Int, lunchMinutes: Int) {
        dao.putSetting(SettingEntity(KEY_WORK_MINUTES, workMinutes.toString()))
        dao.putSetting(SettingEntity(KEY_LUNCH_MINUTES, lunchMinutes.toString()))
    }

    suspend fun setWorkplaceName(name: String) {
        dao.putSetting(SettingEntity(KEY_WORKPLACE_NAME, name.trim().ifEmpty { "Работа" }))
    }

    suspend fun setLunchReminder(enabled: Boolean, leadMinutes: Int) {
        dao.putSetting(SettingEntity(KEY_LUNCH_REMINDER_ENABLED, enabled.toString()))
        dao.putSetting(SettingEntity(KEY_LUNCH_REMINDER_LEAD, leadMinutes.coerceIn(1, 59).toString()))
    }

    suspend fun importBackup(json: String) {
        val payload = BackupCodec.parse(json)
        dao.replaceAll(payload.events, payload.overrides, payload.settings)
    }

    companion object {
        const val KEY_WORK_MINUTES = "work_minutes"
        const val KEY_LUNCH_MINUTES = "lunch_minutes"
        const val KEY_WORKPLACE_NAME = "workplace_name"
        const val KEY_LUNCH_REMINDER_ENABLED = "lunch_reminder_enabled"
        const val KEY_LUNCH_REMINDER_LEAD = "lunch_reminder_lead"
    }
}
