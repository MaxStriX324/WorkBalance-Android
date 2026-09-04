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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.maxstrix.workbalance.MainActivity
import ru.maxstrix.workbalance.R

object LunchReminderScheduler {
    private const val REQUEST_CODE = 4102
    private const val EXTRA_LEAD_MINUTES = "lead_minutes"
    internal const val NOTIFICATION_ID = 4104

    fun schedule(context: Context, delayMinutes: Long, leadMinutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, leadMinutes)
        alarmManager.cancel(pendingIntent)
        val triggerAt = SystemClock.elapsedRealtime() + maxOf(1_000L, delayMinutes * 60_000L)
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, 15))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun pendingIntent(context: Context, leadMinutes: Int): PendingIntent {
        val intent = Intent(context, LunchReminderReceiver::class.java)
            .putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun leadMinutes(intent: Intent): Int = intent.getIntExtra(EXTRA_LEAD_MINUTES, 15)
}

class LunchReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Напоминание об обеде", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Предупреждает перед окончанием обеда"
                }
            )
        }

        val lead = LunchReminderScheduler.leadMinutes(intent)
        val openApp = PendingIntent.getActivity(
            context,
            4103,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Обед заканчивается")
            .setContentText("До окончания обеда осталось $lead мин.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(context).notify(LunchReminderScheduler.NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "lunch_reminder"
    }
}
