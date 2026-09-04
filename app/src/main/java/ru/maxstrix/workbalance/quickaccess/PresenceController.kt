package ru.maxstrix.workbalance.quickaccess

import android.content.Context
import ru.maxstrix.workbalance.WorkBalanceApplication
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.WorkTimeCalculator
import ru.maxstrix.workbalance.notification.LunchReminderScheduler
import java.time.LocalDateTime
import kotlin.math.max

class PresenceController(context: Context) {
    private val appContext = context.applicationContext
    private val repository = (appContext as WorkBalanceApplication).repository

    suspend fun toggle(now: LocalDateTime = LocalDateTime.now()): EventType {
        val data = repository.snapshot()
        val date = now.toLocalDate()
        val today = WorkTimeCalculator.calculateDay(
            date = date,
            rawEvents = data.events,
            schedule = data.schedule,
            override = data.overrides[date],
            now = now,
            productionCalendar = data.productionCalendar
        )
        val leaving = today.isCurrentlyInside
        val type = if (leaving) EventType.OUT else EventType.IN
        repository.addEvent(now, type)

        if (leaving && WorkTimeCalculator.shouldScheduleLunchReminder(
                today,
                data.lunchReminderEnabled,
                data.lunchReminderLeadMinutes
            )
        ) {
            val remainingLunch = max(0, data.schedule.lunchMinutes.toLong() - today.outsideMinutes)
            LunchReminderScheduler.schedule(
                appContext,
                delayMinutes = remainingLunch - data.lunchReminderLeadMinutes,
                leadMinutes = data.lunchReminderLeadMinutes
            )
        } else {
            LunchReminderScheduler.cancel(appContext)
        }

        QuickAccessUpdater.refresh(appContext)
        return type
    }
}
