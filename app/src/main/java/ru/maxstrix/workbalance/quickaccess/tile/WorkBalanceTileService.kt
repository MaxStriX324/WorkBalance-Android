package ru.maxstrix.workbalance.quickaccess.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.maxstrix.workbalance.AppLocale
import ru.maxstrix.workbalance.R
import ru.maxstrix.workbalance.WorkBalanceApplication
import ru.maxstrix.workbalance.domain.WorkTimeCalculator
import ru.maxstrix.workbalance.quickaccess.PresenceController
import java.time.LocalDateTime

class WorkBalanceTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            scope.launch {
                runCatching { PresenceController(applicationContext).toggle() }
                refreshTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        scope.launch {
            val tile = qsTile ?: return@launch
            val repository = (applicationContext as WorkBalanceApplication).repository
            val data = repository.snapshot()
            val now = LocalDateTime.now()
            val date = now.toLocalDate()
            val today = WorkTimeCalculator.calculateDay(
                date, data.events, data.schedule, data.overrides[date], now,
                data.productionCalendar
            )
            val localizedContext = AppLocale.wrap(applicationContext)
            tile.label = localizedContext.getString(R.string.app_name_short)
            tile.state = if (today.isCurrentlyInside) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.contentDescription = localizedContext.getString(
                if (today.isCurrentlyInside) R.string.tile_at_work_description else R.string.tile_away_description
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = localizedContext.getString(
                    if (today.isCurrentlyInside) R.string.tile_at_work_subtitle else R.string.tile_away_subtitle
                )
            }
            tile.updateTile()
        }
    }
}
