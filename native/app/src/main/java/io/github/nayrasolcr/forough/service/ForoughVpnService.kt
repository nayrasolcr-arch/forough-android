package io.github.nayrasolcr.forough.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.MainActivity
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.net.NetTools
import io.github.nayrasolcr.forough.tunnel.ForoughTunnelManager
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Whole-device VPN service — the Android counterpart of the desktop client's
 * sing-box TUN layer. Runs the ForoughTunnelManager engine and keeps a
 * foreground notification with the live status (connect / disconnect
 * actions, like the desktop's header controls).
 *
 * Threading contract (the "stuck disconnect button" fix): every heavy
 * operation — engine start AND teardown — runs on a dedicated background
 * executor. onStartCommand only touches the notification and flips the
 * state bus, so the UI can never freeze no matter how slow the device.
 */
class ForoughVpnService : VpnService() {

    private var manager: ForoughTunnelManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "forough-service-worker")
    }
    @Volatile private var stopPending = false
    @Volatile private var connectAfterStop = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        stopPending = false
        connectAfterStop = false
        createChannel()
        manager = ForoughTunnelManager(applicationContext)

        scope.launch {
            ForoughBus.stats.collect { stats ->
                if (instance != null) {
                    updateNotification(stats.status, stats.tunnelProtocol, stats.egressRegion)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                // Android requires every startForegroundService call to be
                // answered with a startForeground() — flash the notification
                // before tearing down (skipping it crashes with
                // ForegroundServiceDidNotStartInTime).
                startInForeground(getString(R.string.notif_disconnecting))
                if (stopPending) return START_NOT_STICKY
                stopPending = true
                val m = manager
                if (m == null) {
                    finishStop()
                } else {
                    // The engine publishes running=false synchronously, so
                    // the UI button unlocks the moment this intent lands;
                    // the blocking teardown then runs on the worker thread.
                    m.requestStop { finishStop() }
                }
                // Not sticky: after an explicit disconnect the system must
                // not resurrect the tunnel (the "won't stay disconnected"
                // symptom).
                return START_NOT_STICKY
            }
            else -> {
                if (intent == null) {
                    // Sticky restart after the system killed the service:
                    // resume only when a session is supposed to be live,
                    // otherwise stay dead.
                    if (!ForoughBus.running.value) {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                if (stopPending) {
                    // A teardown is in flight — queue the connect so it fires
                    // the moment the stop finishes (fast disconnect→connect
                    // taps must never be swallowed).
                    connectAfterStop = true
                    return START_STICKY
                }
                if (ForoughBus.running.value) {
                    // The tunnel is already up — do not double-start.
                    return START_STICKY
                }
                startInForeground(getString(R.string.notif_connecting))
                val options = SettingsStore(applicationContext).load()
                val m = manager
                if (m == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // startAsync runs the engine bring-up on the manager's
                // lifecycle thread — off the main thread, serialized with
                // any pending stop.
                m.startAsync(options)
                return START_STICKY
            }
        }
    }

    private fun finishStop() {
        stopPending = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (connectAfterStop) {
            connectAfterStop = false
            // Reconnect requested while the teardown was running — start a
            // fresh session now that the engine is fully stopped.
            val connect = Intent(this, ForoughVpnService::class.java)
                .setAction(ACTION_CONNECT)
            ContextCompat.startForegroundService(this, connect)
        }
    }

    override fun onRevoke() {
        // The user (or system) revoked VPN consent — tear down honestly,
        // like the desktop client's fallback_disconnect. Also off the main
        // thread; onRevoke runs on the UI thread.
        ForoughBus.appendLog("VPN permission revoked — tunnel stopped")
        startInForeground(getString(R.string.notif_disconnecting))
        val m = manager
        if (m == null) {
            finishStop()
        } else {
            m.requestStop { finishStop() }
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        // Emergency sync teardown if the system is destroying a live
        // service — best effort, on the worker so onDestroy returns fast.
        val m = manager
        if (m != null && ForoughBus.running.value) {
            worker.execute { try { m.stop() } catch (_: Exception) {} }
        }
        worker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun startInForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String, protocol: String, region: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText(status), protocol, region))
    }

    private fun buildNotification(text: String): Notification = buildNotification(text, "-", "-")

    private fun buildNotification(text: String, protocol: String, region: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ForoughVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stats = ForoughBus.stats.value
        val connected = stats.status == "connected"
        val content = when {
            connected -> {
                // Live speeds + region + protocol right in the shade —
                // the "when I connect, something should show up" answer.
                val speed = "↓ ${NetTools.humanSpeed(stats.downstreamBps)} · ↑ ${NetTools.humanSpeed(stats.upstreamBps)}"
                if (region != "-" && region.isNotBlank()) "$text · ${RegionLabel.of(region)} · $protocol\n$speed"
                else "$text · $protocol\n$speed"
            }
            else -> text
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forough)
            .setContentTitle(getString(R.string.app_name))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notif_disconnect), disconnectIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (connected && stats.connectedAtMs > 0) {
            // Elapsed-time chronometer in the shade, like professional VPN
            // clients — visible proof the session is alive.
            builder.setWhen(stats.connectedAtMs)
            builder.setUsesChronometer(true)
        } else {
            builder.setUsesChronometer(false)
        }

        return builder.build()
    }

    private fun statusText(status: String): String = when (status) {
        "connected" -> getString(R.string.notif_connected)
        "connecting" -> getString(R.string.notif_connecting)
        "reconnecting" -> getString(R.string.notif_reconnecting)
        "disconnecting", "exiting" -> getString(R.string.notif_disconnecting)
        else -> getString(R.string.notif_disconnected)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.notif_channel_desc)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** Region code → flag emoji for the notification line. */
    private object RegionLabel {
        fun of(code: String): String {
            if (code.length != 2 || !code.all { it in 'A'..'Z' }) return code
            val base = 0x1F1E6
            return String(Character.toChars(base + (code[0] - 'A'))) +
                String(Character.toChars(base + (code[1] - 'A')))
        }
    }

    companion object {
        const val ACTION_CONNECT = "io.github.nayrasolcr.forough.CONNECT"
        const val ACTION_DISCONNECT = "io.github.nayrasolcr.forough.DISCONNECT"
        private const val CHANNEL_ID = "forough_tunnel"
        private const val NOTIFICATION_ID = 1001

        @Volatile var instance: ForoughVpnService? = null
    }
}
