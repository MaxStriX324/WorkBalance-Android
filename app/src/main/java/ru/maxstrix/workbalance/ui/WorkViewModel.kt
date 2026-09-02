package ru.maxstrix.workbalance.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.max

data class WorkUiState(
    val loading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val workplaceName: String = "Работа",
    val schedule: Schedule = Schedule(),
    val today: DayResult? = null,
    val month: MonthResult? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val previousBalanceMinutes: Long = 0,
    val lunchReminderEnabled: Boolean = true,
    val lunchReminderLeadMinutes: Int = 15,
    val normalExit: LocalDateTime? = null,
    val earliestExit: LocalDateTime? = null,
    val data: WorkData? = null
)

class WorkViewModel(
    private val repository: WorkRepository,
    private val appContext: Context
) : ViewModel() {
    private val now = MutableStateFlow(LocalDateTime.now())
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val state = combine(repository.data, now, selectedMonth) { data, clock, month ->
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
        val normalNeed = max(0, today.requiredMinutes - today.creditedMinutes)
        val usablePreviousBalance = max(0, previousBalance)
        val earliestTarget = max(0, today.requiredMinutes - usablePreviousBalance)
        val earliestNeed = max(0, earliestTarget - today.creditedMinutes)

        WorkUiState(
            loading = false,
            now = clock,
            workplaceName = data.workplaceName,
            schedule = data.schedule,
            today = today,
            month = monthResult,
            selectedMonth = month,
            previousBalanceMinutes = previousBalance,
            lunchReminderEnabled = data.lunchReminderEnabled,
            lunchReminderLeadMinutes = data.lunchReminderLeadMinutes,
            normalExit = if (today.isCurrentlyInside) clock.plusMinutes(normalNeed) else null,
            earliestExit = if (today.isCurrentlyInside) clock.plusMinutes(earliestNeed) else null,
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

    companion object {
        fun factory(repository: WorkRepository, appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkViewModel(repository, appContext.applicationContext) as T
            }
    }
}
