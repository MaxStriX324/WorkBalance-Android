package ru.maxstrix.workbalance.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.DayOverride
import ru.maxstrix.workbalance.domain.CalendarRegion
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.ProductionCalendar
import ru.maxstrix.workbalance.domain.ProductionCalendarSettings
import ru.maxstrix.workbalance.domain.Schedule
import ru.maxstrix.workbalance.domain.ShortenedDayMode
import ru.maxstrix.workbalance.domain.WorkEvent
import java.time.LocalDate
import java.time.LocalDateTime

data class WorkData(
    val events: List<WorkEvent>,
    val overrides: Map<LocalDate, DayOverride>,
    val schedule: Schedule,
    val workplaceName: String,
    val lunchReminderEnabled: Boolean,
    val lunchReminderLeadMinutes: Int,
    val automaticUpdateCheckEnabled: Boolean,
    val productionCalendar: ProductionCalendar = ProductionCalendar(),
    val availableCalendarYears: Set<Int> = emptySet()
)

class WorkRepository(
    private val dao: WorkDao,
    private val calendarProvider: ProductionCalendarProvider
) {
    val data: Flow<WorkData> = combine(
        dao.observeEvents(), dao.observeOverrides(), dao.observeSettings()
    ) { eventRows, overrideRows, settingsRows ->
        mapData(eventRows, overrideRows, settingsRows)
    }

    suspend fun snapshot(): WorkData = mapData(
        dao.getEvents(), dao.getOverrides(), dao.getSettings()
    )

    suspend fun addEvent(at: LocalDateTime, type: EventType) {
        dao.insertEvent(WorkEventEntity(localDateTime = at.toString(), type = type.name))
    }

    suspend fun addInterval(start: LocalDateTime, end: LocalDateTime) {
        dao.insertEvents(
            listOf(
                WorkEventEntity(localDateTime = start.toString(), type = EventType.IN.name),
                WorkEventEntity(localDateTime = end.toString(), type = EventType.OUT.name)
            )
        )
    }

    suspend fun saveInterval(
        startEvent: WorkEvent,
        endEvent: WorkEvent?,
        start: LocalDateTime,
        end: LocalDateTime
    ) {
        val updated = mutableListOf(
            WorkEventEntity(startEvent.id, start.toString(), EventType.IN.name)
        )
        if (endEvent == null) {
            dao.updateEvents(updated)
            dao.insertEvent(WorkEventEntity(localDateTime = end.toString(), type = EventType.OUT.name))
        } else {
            updated += WorkEventEntity(endEvent.id, end.toString(), EventType.OUT.name)
            dao.updateEvents(updated)
        }
    }

    suspend fun deleteInterval(startEvent: WorkEvent, endEvent: WorkEvent?) {
        dao.deleteEvents(
            listOfNotNull(
                WorkEventEntity(startEvent.id, startEvent.at.toString(), startEvent.type.name),
                endEvent?.let { WorkEventEntity(it.id, it.at.toString(), it.type.name) }
            )
        )
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

    suspend fun setAutomaticUpdateCheck(enabled: Boolean) {
        dao.putSetting(SettingEntity(KEY_AUTOMATIC_UPDATE_CHECK, enabled.toString()))
    }

    suspend fun setProductionCalendarSettings(settings: ProductionCalendarSettings) {
        dao.putSettings(
            listOf(
                SettingEntity(KEY_CALENDAR_FEDERAL_ENABLED, settings.federalEnabled.toString()),
                SettingEntity(KEY_CALENDAR_REGIONAL_ENABLED, settings.regionalEnabled.toString()),
                SettingEntity(KEY_CALENDAR_REGION, settings.region.code),
                SettingEntity(KEY_CALENDAR_SHORTENED_MODE, settings.shortenedDayMode.name)
            )
        )
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
        const val KEY_AUTOMATIC_UPDATE_CHECK = "automatic_update_check"
        const val KEY_CALENDAR_FEDERAL_ENABLED = "calendar_federal_enabled"
        const val KEY_CALENDAR_REGIONAL_ENABLED = "calendar_regional_enabled"
        const val KEY_CALENDAR_REGION = "calendar_region"
        const val KEY_CALENDAR_SHORTENED_MODE = "calendar_shortened_mode"
    }

    private fun mapData(
        eventRows: List<WorkEventEntity>,
        overrideRows: List<DayOverrideEntity>,
        settingsRows: List<SettingEntity>
    ): WorkData {
        val settings = settingsRows.associate { it.key to it.value }
        val calendarSettings = ProductionCalendarSettings(
            federalEnabled = settings[KEY_CALENDAR_FEDERAL_ENABLED]
                ?.toBooleanStrictOrNull() ?: true,
            regionalEnabled = settings[KEY_CALENDAR_REGIONAL_ENABLED]
                ?.toBooleanStrictOrNull() ?: false,
            region = CalendarRegion.fromCode(settings[KEY_CALENDAR_REGION] ?: CalendarRegion.SARATOV.code),
            shortenedDayMode = settings[KEY_CALENDAR_SHORTENED_MODE]
                ?.let { value -> runCatching { ShortenedDayMode.valueOf(value) }.getOrNull() }
                ?: ShortenedDayMode.ASK
        )
        return WorkData(
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
            lunchReminderLeadMinutes = settings[KEY_LUNCH_REMINDER_LEAD]?.toIntOrNull() ?: 15,
            automaticUpdateCheckEnabled = settings[KEY_AUTOMATIC_UPDATE_CHECK]
                ?.toBooleanStrictOrNull() ?: true,
            productionCalendar = calendarProvider.calendar(calendarSettings),
            availableCalendarYears = calendarProvider.availableYears()
        )
    }
}
