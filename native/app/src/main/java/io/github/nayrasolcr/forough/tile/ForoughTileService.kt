package io.github.nayrasolcr.forough.tile

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.MainActivity
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.service.ForoughVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile — connect / disconnect straight from the notification
 * shade without opening the app (power feature). The tile mirrors the
 * engine state live: active while connected, inactive when idle.
 *
 * If VPN consent is still missing the tile opens MainActivity instead —
 * a TileService cannot show the system permission dialog itself.
 */
class ForoughTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        // Sync immediately, then keep following the engine state while the
        // shade holds this tile on screen.
        refreshTile()
        scope.launch {
            ForoughBus.running.collect { refreshTile() }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val running = ForoughBus.running.value
        if (running) {
            val intent = Intent(this, ForoughVpnService::class.java)
                .setAction(ForoughVpnService.ACTION_DISCONNECT)
            ContextCompat.startForegroundService(this, intent)
            refreshTile()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            // Need the consent dialog — only an activity can show it.
            val open = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapseCompat(open)
            return
        }
        val connect = Intent(this, ForoughVpnService::class.java)
            .setAction(ForoughVpnService.ACTION_CONNECT)
        ContextCompat.startForegroundService(this, connect)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val running = ForoughBus.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            // Tile.subtitle exists from Android 10 onward.
            tile.subtitle = if (running) {
                getString(R.string.notif_connected)
            } else {
                getString(R.string.notif_disconnected)
            }
        }
        tile.updateTile()
    }

    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
