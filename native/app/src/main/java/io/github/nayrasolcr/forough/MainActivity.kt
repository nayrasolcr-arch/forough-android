package io.github.nayrasolcr.forough

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.nayrasolcr.forough.service.ForoughVpnService
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import io.github.nayrasolcr.forough.ui.ForoughApp
import io.github.nayrasolcr.forough.ui.theme.ForoughTheme

class MainActivity : ComponentActivity() {

    private var pendingConnect = false
    private lateinit var store: SettingsStore

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingConnect) {
            pendingConnect = false
            sendConnectIntent()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Battery-optimization exemption — the user lands here from settings;
    // result is informational only (the system remembers the choice).
    private val batteryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        val pref = SettingsStore(newBase).language
        super.attachBaseContext(LocaleHelper.wrap(newBase, pref))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)
        requestNotificationPermissionIfNeeded()

        setContent {
            ForoughTheme {
                ForoughApp(
                    onConnect = { requestVpnAndConnect() },
                    onDisconnect = { sendDisconnectIntent() },
                    onLanguageChanged = { recreate() },
                    onBatteryHelper = { requestIgnoreBatteryOptimizations() }
                )
            }
        }

        // Auto-connect (power feature): only on a fresh launch, never after
        // rotation/process restore, and only when no session is running.
        if (savedInstanceState == null && store.autoConnect && !ForoughBus.running.value) {
            pendingConnect = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Fire the deferred auto-connect once the UI is up — doing it inside
        // onCreate races the notification-permission prompt.
        if (pendingConnect && store.autoConnect) {
            pendingConnect = false
            requestVpnAndConnect()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestVpnAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingConnect = true
            vpnPermissionLauncher.launch(intent)
        } else {
            sendConnectIntent()
        }
    }

    private fun sendConnectIntent() {
        val intent = Intent(this, ForoughVpnService::class.java)
            .setAction(ForoughVpnService.ACTION_CONNECT)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendDisconnectIntent() {
        val intent = Intent(this, ForoughVpnService::class.java)
            .setAction(ForoughVpnService.ACTION_DISCONNECT)
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * Ask the system to exempt Forough from battery optimization so
     * aggressive OEM task killers (Xiaomi/Huawei/Samsung) don't murder the
     * foreground tunnel service. Opens the system dialog when not already
     * exempt.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ForoughBus.setToast(getString(R.string.settings_battery_ok))
            return
        }
        try {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            batteryLauncher.launch(intent)
        } catch (_: Exception) {
            // Some OEM builds remove the direct-request action — fall back
            // to the general optimization list.
            try {
                batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }
}
