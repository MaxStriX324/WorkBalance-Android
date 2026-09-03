package ru.maxstrix.workbalance.quickaccess.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.maxstrix.workbalance.MainActivity
import ru.maxstrix.workbalance.R
import ru.maxstrix.workbalance.WorkBalanceApplication
import ru.maxstrix.workbalance.domain.WorkTimeCalculator
import ru.maxstrix.workbalance.quickaccess.PresenceController
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class WorkBalanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAsync(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        val pendingResult = goAsync()
        scope.launch {
            runCatching { PresenceController(context).toggle() }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WorkBalanceWidgetProvider::class.java)
            )
            update(context, ids)
            pendingResult.finish()
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "ru.maxstrix.workbalance.action.WIDGET_TOGGLE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun updateAsync(context: Context, widgetIds: IntArray) {
            val appContext = context.applicationContext
            scope.launch { update(appContext, widgetIds) }
        }

        private suspend fun update(context: Context, widgetIds: IntArray) {
            if (widgetIds.isEmpty()) return
            val repository = (context.applicationContext as WorkBalanceApplication).repository
            val data = repository.snapshot()
            val now = LocalDateTime.now()
            val date = now.toLocalDate()
            val today = WorkTimeCalculator.calculateDay(
                date, data.events, data.schedule, data.overrides[date], now
            )
            val month = WorkTimeCalculator.calculateMonth(
                YearMonth.from(date), data.events, data.schedule, data.overrides, now
            )
            val need = WorkTimeCalculator.minutesUntilCreditedTarget(today, today.requiredMinutes)
            val exitTime = if (today.isCurrentlyInside) now.plusMinutes(need).format(timeFormatter) else "—"
            val manager = AppWidgetManager.getInstance(context)

            widgetIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.work_balance_widget)
                views.setTextViewText(R.id.widget_title, data.workplaceName)
                views.setTextViewText(R.id.widget_status, if (today.isCurrentlyInside) "НА РАБОТЕ" else "НЕ НА РАБОТЕ")
                views.setTextViewText(R.id.widget_today, "Сегодня ${duration(today.creditedMinutes)} / ${duration(today.requiredMinutes)}")
                views.setTextViewText(R.id.widget_balance, "Баланс ${signed(month.balanceToDateMinutes)}")
                views.setTextViewText(R.id.widget_exit, "Уйти $exitTime")
                views.setTextViewText(R.id.widget_toggle, if (today.isCurrentlyInside) "ВЫШЕЛ" else "ВОШЁЛ")
                views.setOnClickPendingIntent(R.id.widget_toggle, toggleIntent(context))
                views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                manager.updateAppWidget(id, views)
            }
        }

        private fun toggleIntent(context: Context): PendingIntent {
            val intent = Intent(context, WorkBalanceWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                context, 5201, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            5202,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun duration(minutes: Long): String = "%d:%02d".format(minutes / 60, minutes % 60)

        private fun signed(minutes: Long): String {
            val sign = when { minutes > 0 -> "+"; minutes < 0 -> "−"; else -> "" }
            return sign + duration(abs(minutes))
        }
    }
}
