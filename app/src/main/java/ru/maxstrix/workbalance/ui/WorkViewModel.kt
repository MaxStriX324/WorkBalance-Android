package ru.maxstrix.workbalance.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.maxstrix.workbalance.BuildConfig
import ru.maxstrix.workbalance.data.BackupCodec
import ru.maxstrix.workbalance.data.WorkData
import ru.maxstrix.workbalance.data.WorkRepository
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.DayResult
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.MonthResult
import ru.maxstrix.workbalance.domain.Schedule
import ru.maxstrix.workbalance.domain.WorkEvent
import ru.maxstrix.workbalance.domain.WorkTimeCalculator
import ru.maxstrix.workbalance.notification.LunchReminderScheduler
import ru.maxstrix.workbalance.quickaccess.PresenceController
import ru.maxstrix.workbalance.quickaccess.QuickAccessUpdater
import ru.maxstrix.workbalance.update.AppRelease
import ru.maxstrix.workbalance.update.AppUpdateChecker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.max

private const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
private const val UPDATE_CHECK_PREFERENCES = "update-check"
private const val LAST_UPDATE_CHECK_KEY = "last-check-millis"

private data class UpdateUiState(
    val checking: Boolean = false,
    val availableRelease: AppRelease? = null,
    val message: String? = null
)

data class WorkUiState(
    val loading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val workplaceName: String = "Работа",
    val schedule: Schedule = Schedule(),
    val today: DayResult? = null,
    val month: MonthResult? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val previousBalanceMinutes: Long = 0,
    val balanceIncludingTodayMinutes: Long = 0,
    val lunchReminderEnabled: Boolean = true,
    val lunchReminderLeadMinutes: Int = 15,
    val automaticUpdateCheckEnabled: Boolean = true,
    val checkingForUpdates: Boolean = false,
    val availableRelease: AppRelease? = null,
    val updateMessage: String? = null,
    val normalExit: LocalDateTime? = null,
    val balanceZeroExit: LocalDateTime? = null,
    val recommendedExit: LocalDateTime? = null,
    val recommendedTodayMinutes: Long = 0,
    val data: WorkData? = null
)

class WorkViewModel(
    private val repository: WorkRepository,
    private val appContext: Context
) : ViewModel() {
    private val now = MutableStateFlow(LocalDateTime.now())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val updateUiState = MutableStateFlow(UpdateUiState())

    val state = combine(repository.data, now, selectedMonth, updateUiState) { data, clock, month, update ->
        val today = WorkTimeCalculator.calculateDay(
            clock.toLocalDate(), data.events, data.schedule,
            data.overrides[clock.toLocalDate()], clock
        )
        val monthResult = WorkTimeCalculator.calculateMonth(
            month, data.events, data.schedule, data.overrides, clock
        )
        val currentMonth = WorkTimeCalculator.calculateMonth(
            YearMonth.from(clock), data.events, data.schedule, data.overrides, clock
        )
        val previousBalance = currentMonth.days
            .filter { it.date.isBefore(clock.toLocalDate()) }
            .sumOf { it.balanceMinutes }
        val balanceIncludingToday = previousBalance + today.balanceMinutes
        val normalNeed = WorkTimeCalculator.minutesUntilCreditedTarget(today, today.requiredMinutes)
        val balanceZeroTarget = max(0, today.requiredMinutes - previousBalance)
        val balanceZeroNeed = WorkTimeCalculator.minutesUntilCreditedTarget(today, balanceZeroTarget)
        val planningDays = currentMonth.days.count { day ->
            !day.date.isBefore(clock.toLocalDate()) &&
                day.requiredMinutes > 0 &&
                data.overrides[day.date]?.kind != DayKind.PLANNED_ABSENCE
        }
        val creditedBeforeToday = currentMonth.days
            .filter { it.date.isBefore(clock.toLocalDate()) }
            .sumOf { it.creditedMinutes }
        val remainingAtDayStart = max(0, currentMonth.planMinutes - creditedBeforeToday)
        val recommendedToday = if (planningDays == 0) 0 else {
            (remainingAtDayStart + planningDays - 1) / planningDays
        }
        val recommendedNeed = WorkTimeCalculator.minutesUntilCreditedTarget(today, recommendedToday)

        WorkUiState(
            loading = false,
            now = clock,
            workplaceName = data.workplaceName,
            schedule = data.schedule,
            today = today,
            month = monthResult,
            selectedMonth = month,
            previousBalanceMinutes = previousBalance,
            balanceIncludingTodayMinutes = balanceIncludingToday,
            lunchReminderEnabled = data.lunchReminderEnabled,
            lunchReminderLeadMinutes = data.lunchReminderLeadMinutes,
            automaticUpdateCheckEnabled = data.automaticUpdateCheckEnabled,
            checkingForUpdates = update.checking,
            availableRelease = update.availableRelease,
            updateMessage = update.message,
            normalExit = if (today.isCurrentlyInside) clock.plusMinutes(normalNeed) else null,
            balanceZeroExit = if (today.isCurrentlyInside) clock.plusMinutes(balanceZeroNeed) else null,
            recommendedExit = if (today.isCurrentlyInside && recommendedToday > 0) {
                clock.plusMinutes(recommendedNeed)
            } else null,
            recommendedTodayMinutes = recommendedToday,
            data = data
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                now.value = LocalDateTime.now()
            }
        }
        viewModelScope.launch {
            val data = repository.data.first()
            if (data.automaticUpdateCheckEnabled && automaticUpdateCheckIsDue()) {
                markAutomaticUpdateCheck()
                checkForUpdates(showCurrentVersionMessage = false)
            }
        }
    }

    fun togglePresence() = viewModelScope.launch {
        PresenceController(appContext).toggle()
        now.value = LocalDateTime.now()
    }

    fun addEvent(at: LocalDateTime, type: EventType) = viewModelScope.launch {
        repository.addEvent(at, type)
        QuickAccessUpdater.refresh(appContext)
        now.value = LocalDateTime.now()
    }

    fun updateEvent(event: WorkEvent) = viewModelScope.launch {
        repository.updateEvent(event)
        QuickAccessUpdater.refresh(appContext)
        now.value = LocalDateTime.now()
    }

    fun deleteEvent(event: WorkEvent) = viewModelScope.launch {
        repository.deleteEvent(event)
        QuickAccessUpdater.refresh(appContext)
        now.value = LocalDateTime.now()
    }

    fun addInterval(start: LocalDateTime, end: LocalDateTime) = viewModelScope.launch {
        repository.addInterval(start, end)
        QuickAccessUpdater.refresh(appContext)
        now.value = LocalDateTime.now()
    }

    fun saveInterval(
        startEvent: WorkEvent,
        endEvent: WorkEvent?,
        start: LocalDateTime,
        end: LocalDateTime
    ) = viewModelScope.launch {
        repository.saveInterval(startEvent, endEvent, start, end)
        QuickAccessUpdater.refresh(appContext)
        now.value = LocalDateTime.now()
    }

    fun deleteInterval(startEvent: WorkEvent, endEvent: WorkEvent?) = viewModelScope.launch {
        repository.deleteInterval(startEvent, endEvent)
        QuickAccessUpdater.refresh(appContext)
        now.value = LocalDateTime.now()
    }

    fun setMonth(month: YearMonth) { selectedMonth.value = month }

    fun setDay(date: LocalDate, kind: DayKind) = viewModelScope.launch {
        repository.setDay(date, kind)
        QuickAccessUpdater.refresh(appContext)
    }

    fun setSchedule(workMinutes: Int, lunchMinutes: Int) = viewModelScope.launch {
        repository.setSchedule(workMinutes.coerceAtLeast(1), lunchMinutes.coerceAtLeast(0))
        QuickAccessUpdater.refresh(appContext)
    }

    fun setWorkplaceName(name: String) = viewModelScope.launch {
        repository.setWorkplaceName(name)
        QuickAccessUpdater.refresh(appContext)
    }

    fun setLunchReminder(enabled: Boolean, leadMinutes: Int) = viewModelScope.launch {
        repository.setLunchReminder(enabled, leadMinutes)
        if (!enabled) LunchReminderScheduler.cancel(appContext)
        QuickAccessUpdater.refresh(appContext)
    }

    fun saveSettings(
        name: String,
        workMinutes: Int,
        lunchMinutes: Int,
        reminderEnabled: Boolean,
        reminderLeadMinutes: Int,
        automaticUpdateCheckEnabled: Boolean,
        onSaved: () -> Unit
    ) = viewModelScope.launch {
        repository.setWorkplaceName(name)
        repository.setSchedule(workMinutes.coerceAtLeast(1), lunchMinutes.coerceAtLeast(0))
        repository.setLunchReminder(reminderEnabled, reminderLeadMinutes)
        repository.setAutomaticUpdateCheck(automaticUpdateCheckEnabled)
        if (!reminderEnabled) LunchReminderScheduler.cancel(appContext)
        QuickAccessUpdater.refresh(appContext)
        onSaved()
    }

    fun checkForUpdates(showCurrentVersionMessage: Boolean = true) {
        if (updateUiState.value.checking) return
        viewModelScope.launch {
            updateUiState.value = UpdateUiState(checking = true)
            runCatching { AppUpdateChecker.latestRelease(BuildConfig.VERSION_NAME) }
                .onSuccess { release ->
                    updateUiState.value = if (AppUpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                        UpdateUiState(availableRelease = release)
                    } else {
                        UpdateUiState(
                            message = if (showCurrentVersionMessage) {
                                "Установлена актуальная версия ${BuildConfig.VERSION_NAME}"
                            } else null
                        )
                    }
                }
                .onFailure {
                    updateUiState.value = UpdateUiState(
                        message = if (showCurrentVersionMessage) {
                            "Не удалось проверить обновления. Проверьте подключение к интернету."
                        } else null
                    )
                }
        }
    }

    fun dismissAvailableRelease() {
        updateUiState.value = updateUiState.value.copy(availableRelease = null)
    }

    fun consumeUpdateMessage() {
        updateUiState.value = updateUiState.value.copy(message = null)
    }

    fun backupJson(): String = BackupCodec.encode(
        requireNotNull(state.value.data) { "Данные приложения ещё не загружены" }
    )

    fun monthCsv(): String = BackupCodec.monthCsv(
        requireNotNull(state.value.month) { "Месяц ещё не рассчитан" }
    )

    fun importBackup(json: String, onResult: (String) -> Unit) = viewModelScope.launch {
        runCatching { repository.importBackup(json) }
            .onSuccess {
                QuickAccessUpdater.refresh(appContext)
                onResult("Резервная копия восстановлена")
            }
            .onFailure { error -> onResult("Не удалось восстановить: ${error.message ?: "неизвестная ошибка"}") }
    }

    private fun automaticUpdateCheckIsDue(): Boolean {
        val lastCheck = updatePreferences().getLong(LAST_UPDATE_CHECK_KEY, 0L)
        return System.currentTimeMillis() - lastCheck >= UPDATE_CHECK_INTERVAL_MILLIS
    }

    private fun markAutomaticUpdateCheck() {
        updatePreferences().edit()
            .putLong(LAST_UPDATE_CHECK_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun updatePreferences() =
        appContext.getSharedPreferences(UPDATE_CHECK_PREFERENCES, Context.MODE_PRIVATE)

    companion object {
        fun factory(repository: WorkRepository, appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkViewModel(repository, appContext.applicationContext) as T
            }
    }
}
