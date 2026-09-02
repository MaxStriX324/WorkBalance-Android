package ru.maxstrix.workbalance.quickaccess.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
                date, data.events, data.schedule, data.overrides[date], now
            )
            tile.label = "WorkBalance"
            tile.state = if (today.isCurrentlyInside) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.contentDescription = if (today.isCurrentlyInside) "На работе. Нажмите, чтобы выйти" else "Не на работе. Нажмите, чтобы войти"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (today.isCurrentlyInside) "На работе · нажать для выхода" else "Не на работе · нажать для входа"
            }
            tile.updateTile()
        }
    }
}
