package ru.maxstrix.workbalance.quickaccess

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import ru.maxstrix.workbalance.quickaccess.tile.WorkBalanceTileService
import ru.maxstrix.workbalance.quickaccess.widget.WorkBalanceWidgetProvider

object QuickAccessUpdater {
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, WorkBalanceWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isNotEmpty()) {
            WorkBalanceWidgetProvider.updateAsync(appContext, widgetIds)
        }
        TileService.requestListeningState(
            appContext,
            ComponentName(appContext, WorkBalanceTileService::class.java)
        )
    }
}
