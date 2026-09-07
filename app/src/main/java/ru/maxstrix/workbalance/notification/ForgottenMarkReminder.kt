package ru.maxstrix.workbalance.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.maxstrix.workbalance.MainActivity
import ru.maxstrix.workbalance.R
import ru.maxstrix.workbalance.WorkBalanceApplication
import ru.maxstrix.workbalance.data.WorkData
import ru.maxstrix.workbalance.domain.DayResult
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.ForgottenMarkReminderPolicy
import ru.maxstrix.workbalance.domain.WorkTimeCalculator
import ru.maxstrix.workbalance.quickaccess.PresenceController
import ru.maxstrix.workbalance.quickaccess.QuickAccessUpdater
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private const val ACTION_MARK_ENTRY = "ru.maxstrix.workbalance.action.MARK_ENTRY"
private const val ACTION_MARK_EXIT = "ru.maxstrix.workbalance.action.MARK_EXIT"
private const val ACTION_SNOOZE_ENTRY = "ru.maxstrix.workbalance.action.SNOOZE_ENTRY"
private const val ACTION_SNOOZE_EXIT = "ru.maxstrix.workbalance.action.SNOOZE_EXIT"
private const val ACTION_NOT_WORKING_TODAY = "ru.maxstrix.workbalance.action.NOT_WORKING_TODAY"

internal enum class ForgottenMarkReminderKind {
    ENTRY,
    EXIT
}

object ForgottenMarkReminderScheduler {
    private const val ENTRY_REQUEST_CODE = 4201
    private const val EXIT_REQUEST_CODE = 4202
    private const val EXTRA_KIND = "forgotten_mark_kind"
    private const val SNOOZE_PREFERENCES = "forgotten-mark-reminders"
    private const val ENTRY_SNOOZE_UNTIL = "entry-snooze-until"
    private const val EXIT_SNOOZE_UNTIL = "exit-snooze-until"
    internal const val ENTRY_NOTIFICATION_ID = 4204
    internal const val EXIT_NOTIFICATION_ID = 4205

    fun refreshAsync(context: Context) {
        val appContext = context.applicationContext
        reminderScope.launch { refresh(appContext) }
    }

    internal suspend fun refresh(
        context: Context,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val appContext = context.applicationContext
        val data = (appContext as WorkBalanceApplication).repository.snapshot()
        cancelAlarm(appContext, ForgottenMarkReminderKind.ENTRY)
        cancelAlarm(appContext, ForgottenMarkReminderKind.EXIT)

        if (!data.forgottenMarkReminderSettings.enabled) {
            clearAllSnoozes(appContext)
            cancelNotifications(appContext)
            return
        }

        val today = calculateDay(data, now.toLocalDate(), now)
        if (!ForgottenMarkReminderPolicy.shouldRemindEntry(today, data.overrides[today.date])) {
            clearSnooze(appContext, ForgottenMarkReminderKind.ENTRY)
            NotificationManagerCompat.from(appContext).cancel(ENTRY_NOTIFICATION_ID)
        }
        if (!ForgottenMarkReminderPolicy.shouldRemindExit(today)) {
            clearSnooze(appContext, ForgottenMarkReminderKind.EXIT)
            NotificationManagerCompat.from(appContext).cancel(EXIT_NOTIFICATION_ID)
        }
        scheduleNextEntry(appContext, data, now)
        scheduleExitIfNeeded(appContext, data, now)
    }

    internal suspend fun handleAlarm(
        context: Context,
        kind: ForgottenMarkReminderKind,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val appContext = context.applicationContext
        clearSnooze(appContext, kind)
        val data = (appContext as WorkBalanceApplication).repository.snapshot()
        if (!data.forgottenMarkReminderSettings.enabled) {
            cancelNotifications(appContext)
            return
        }

        val today = calculateDay(data, now.toLocalDate(), now)
        val shouldNotify = when (kind) {
            ForgottenMarkReminderKind.ENTRY -> ForgottenMarkReminderPolicy.shouldRemindEntry(
                today,
                data.overrides[today.date]
            )
            ForgottenMarkReminderKind.EXIT -> ForgottenMarkReminderPolicy.shouldRemindExit(today)
        }
        if (shouldNotify) {
            ForgottenMarkNotification.show(appContext, kind, data.workplaceName)
        } else {
            NotificationManagerCompat.from(appContext).cancel(notificationId(kind))
        }

        if (kind == ForgottenMarkReminderKind.ENTRY) {
            scheduleNextEntry(appContext, data, now.plusSeconds(1))
        }
    }

    internal fun snooze(context: Context, kind: ForgottenMarkReminderKind, minutes: Int) {
        val delayMillis = minutes.coerceIn(5, 240) * 60_000L
        val snoozeUntil = System.currentTimeMillis() + delayMillis
        context.getSharedPreferences(SNOOZE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(snoozeKey(kind), snoozeUntil)
            .apply()
        scheduleAt(
            context.applicationContext,
            kind,
            snoozeUntil
        )
        NotificationManagerCompat.from(context).cancel(notificationId(kind))
    }

    fun cancel(context: Context) {
        cancelAlarm(context, ForgottenMarkReminderKind.ENTRY)
        cancelAlarm(context, ForgottenMarkReminderKind.EXIT)
        cancelNotifications(context)
        clearAllSnoozes(context)
    }

    private fun scheduleNextEntry(context: Context, data: WorkData, now: LocalDateTime) {
        val snoozeUntil = snoozeUntil(context, ForgottenMarkReminderKind.ENTRY)
        if (snoozeUntil > System.currentTimeMillis()) {
            scheduleAt(context, ForgottenMarkReminderKind.ENTRY, snoozeUntil)
            return
        }
        val checkTime = data.forgottenMarkReminderSettings.entryCheckTime
        val next = (0L..370L).firstNotNullOfOrNull { offset ->
            val date = now.toLocalDate().plusDays(offset)
            val target = date.atTime(checkTime)
            if (!target.isAfter(now)) return@firstNotNullOfOrNull null
            val required = WorkTimeCalculator.requiredMinutes(
                date,
                data.schedule,
                data.overrides[date],
                data.productionCalendar
            )
            target.takeIf {
                ForgottenMarkReminderPolicy.isAvailableWorkDay(required, data.overrides[date])
            }
        } ?: return
        scheduleAt(context, ForgottenMarkReminderKind.ENTRY, next.toEpochMillis())
    }

    private fun scheduleExitIfNeeded(context: Context, data: WorkData, now: LocalDateTime) {
        val today = calculateDay(data, now.toLocalDate(), now)
        if (!ForgottenMarkReminderPolicy.shouldRemindExit(today)) return
        val snoozeUntil = snoozeUntil(context, ForgottenMarkReminderKind.EXIT)
        if (snoozeUntil > System.currentTimeMillis()) {
            scheduleAt(context, ForgottenMarkReminderKind.EXIT, snoozeUntil)
            return
        }
        val delayMinutes = ForgottenMarkReminderPolicy.exitDelayMinutes(
            today,
            data.forgottenMarkReminderSettings.exitGraceMinutes
        )
        scheduleAt(
            context,
            ForgottenMarkReminderKind.EXIT,
            System.currentTimeMillis() + maxOf(1_000L, delayMinutes * 60_000L)
        )
    }

    private fun calculateDay(data: WorkData, date: LocalDate, now: LocalDateTime): DayResult =
        WorkTimeCalculator.calculateDay(
            date = date,
            rawEvents = data.events,
            schedule = data.schedule,
            override = data.overrides[date],
            now = now,
            productionCalendar = data.productionCalendar
        )

    private fun scheduleAt(context: Context, kind: ForgottenMarkReminderKind, epochMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmPendingIntent(context, kind)
        manager.cancel(pendingIntent)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
    }

    private fun cancelAlarm(context: Context, kind: ForgottenMarkReminderKind) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(alarmPendingIntent(context, kind))
    }

    private fun alarmPendingIntent(context: Context, kind: ForgottenMarkReminderKind): PendingIntent {
        val intent = Intent(context, ForgottenMarkReminderReceiver::class.java)
            .putExtra(EXTRA_KIND, kind.name)
        return PendingIntent.getBroadcast(
            context,
            requestCode(kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun requestCode(kind: ForgottenMarkReminderKind): Int = when (kind) {
        ForgottenMarkReminderKind.ENTRY -> ENTRY_REQUEST_CODE
        ForgottenMarkReminderKind.EXIT -> EXIT_REQUEST_CODE
    }

    private fun notificationId(kind: ForgottenMarkReminderKind): Int = when (kind) {
        ForgottenMarkReminderKind.ENTRY -> ENTRY_NOTIFICATION_ID
        ForgottenMarkReminderKind.EXIT -> EXIT_NOTIFICATION_ID
    }

    private fun snoozeUntil(context: Context, kind: ForgottenMarkReminderKind): Long =
        context.getSharedPreferences(SNOOZE_PREFERENCES, Context.MODE_PRIVATE)
            .getLong(snoozeKey(kind), 0L)

    private fun clearSnooze(context: Context, kind: ForgottenMarkReminderKind) {
        context.getSharedPreferences(SNOOZE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(snoozeKey(kind))
            .apply()
    }

    private fun clearAllSnoozes(context: Context) {
        context.getSharedPreferences(SNOOZE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun snoozeKey(kind: ForgottenMarkReminderKind): String = when (kind) {
        ForgottenMarkReminderKind.ENTRY -> ENTRY_SNOOZE_UNTIL
        ForgottenMarkReminderKind.EXIT -> EXIT_SNOOZE_UNTIL
    }

    private fun cancelNotifications(context: Context) {
        NotificationManagerCompat.from(context).apply {
            cancel(ENTRY_NOTIFICATION_ID)
            cancel(EXIT_NOTIFICATION_ID)
        }
    }

    internal fun kind(intent: Intent): ForgottenMarkReminderKind? =
        intent.getStringExtra(EXTRA_KIND)
            ?.let { value -> runCatching { ForgottenMarkReminderKind.valueOf(value) }.getOrNull() }
}

class ForgottenMarkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = ForgottenMarkReminderScheduler.kind(intent) ?: return
        val pendingResult = goAsync()
        reminderScope.launch {
            try {
                ForgottenMarkReminderScheduler.handleAlarm(context, kind)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ForgottenMarkReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        reminderScope.launch {
            try {
                val app = appContext as WorkBalanceApplication
                when (action) {
                    ACTION_MARK_ENTRY -> {
                        NotificationManagerCompat.from(appContext)
                            .cancel(ForgottenMarkReminderScheduler.ENTRY_NOTIFICATION_ID)
                        PresenceController(appContext).mark(EventType.IN)
                    }
                    ACTION_MARK_EXIT -> {
                        NotificationManagerCompat.from(appContext)
                            .cancel(ForgottenMarkReminderScheduler.EXIT_NOTIFICATION_ID)
                        PresenceController(appContext).mark(EventType.OUT)
                    }
                    ACTION_SNOOZE_ENTRY, ACTION_SNOOZE_EXIT -> {
                        val settings = app.repository.snapshot().forgottenMarkReminderSettings
                        val kind = if (action == ACTION_SNOOZE_ENTRY) {
                            ForgottenMarkReminderKind.ENTRY
                        } else {
                            ForgottenMarkReminderKind.EXIT
                        }
                        ForgottenMarkReminderScheduler.snooze(appContext, kind, settings.snoozeMinutes)
                    }
                    ACTION_NOT_WORKING_TODAY -> {
                        app.repository.setDay(LocalDate.now(), ru.maxstrix.workbalance.domain.DayKind.PLANNED_ABSENCE)
                        QuickAccessUpdater.refresh(appContext)
                        ForgottenMarkReminderScheduler.refresh(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pendingResult = goAsync()
        reminderScope.launch {
            try {
                ForgottenMarkReminderScheduler.refresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private object ForgottenMarkNotification {
    private const val CHANNEL_ID = "forgotten_marks"

    fun show(context: Context, kind: ForgottenMarkReminderKind, workplaceName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            4210,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (kind == ForgottenMarkReminderKind.ENTRY) "Вход не отмечен" else "Выход не отмечен")
            .setContentText(
                if (kind == ForgottenMarkReminderKind.ENTRY) {
                    "В $workplaceName сегодня ещё нет отметки о входе."
                } else {
                    "Расчётная смена закончилась, но приложение считает, что вы на работе."
                }
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)

        if (kind == ForgottenMarkReminderKind.ENTRY) {
            builder.addAction(0, "Войти сейчас", action(context, ACTION_MARK_ENTRY, 4211))
            builder.addAction(0, "Напомнить позже", action(context, ACTION_SNOOZE_ENTRY, 4212))
            builder.addAction(0, "Сегодня не работаю", action(context, ACTION_NOT_WORKING_TODAY, 4213))
        } else {
            builder.addAction(0, "Выйти сейчас", action(context, ACTION_MARK_EXIT, 4214))
            builder.addAction(0, "Напомнить позже", action(context, ACTION_SNOOZE_EXIT, 4215))
        }

        val notificationId = if (kind == ForgottenMarkReminderKind.ENTRY) {
            ForgottenMarkReminderScheduler.ENTRY_NOTIFICATION_ID
        } else {
            ForgottenMarkReminderScheduler.EXIT_NOTIFICATION_ID
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ForgottenMarkReminderActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Забытые отметки", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминает отметить вход или выход"
            }
        )
    }
}
