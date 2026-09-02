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
            now = now
        )
        val leaving = today.isCurrentlyInside
        val type = if (leaving) EventType.OUT else EventType.IN
        repository.addEvent(now, type)

        if (leaving && data.lunchReminderEnabled) {
            val remainingLunch = max(0, data.schedule.lunchMinutes.toLong() - today.outsideMinutes)
            if (remainingLunch > 0) {
                val delayMinutes = max(0, remainingLunch - data.lunchReminderLeadMinutes)
                LunchReminderScheduler.schedule(
                    appContext,
                    delayMinutes = delayMinutes,
                    leadMinutes = minOf(remainingLunch, data.lunchReminderLeadMinutes.toLong()).toInt()
                )
            } else {
                LunchReminderScheduler.cancel(appContext)
            }
        } else {
            LunchReminderScheduler.cancel(appContext)
        }

        QuickAccessUpdater.refresh(appContext)
        return type
    }
}
