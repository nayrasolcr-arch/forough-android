package io.github.nayrasolcr.forough

import io.github.nayrasolcr.forough.engine.XrayServer
import io.github.nayrasolcr.forough.net.NetCondition
import io.github.nayrasolcr.forough.tunnel.ForoughTunnelManager
import io.github.nayrasolcr.forough.tunnel.TunnelStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide state bus between the VPN service (engine) and the Compose UI
 * — the counterpart of the desktop client's GObject signals
 * (stats_changed / running_changed / log_line / error).
 */
object ForoughBus {
    private val _stats = MutableStateFlow(TunnelStats())
    val stats: StateFlow<TunnelStats> = _stats

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    // v2.5.0 — second engine + smart condition detection.
    private val _engineState = MutableStateFlow("")
    val engineState: StateFlow<String> = _engineState

    private val _engineServer = MutableStateFlow<XrayServer?>(null)
    val engineServer: StateFlow<XrayServer?> = _engineServer

    private val _netCondition = MutableStateFlow<NetCondition?>(null)
    val netCondition: StateFlow<NetCondition?> = _netCondition

    fun publishStats(s: TunnelStats) {
        _stats.value = s
    }

    fun publishRunning(running: Boolean) {
        _running.value = running
    }

    fun appendLog(line: String) {
        val trimmed = line.take(2000)
        val list = _logs.value
        val next = if (list.size >= 800) list.drop(list.size - 700) else list
        _logs.value = next + trimmed
    }

    /** User-initiated clear (Logs screen). */
    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun setToast(message: String?) {
        _toast.value = message
    }

    fun consumeToast(): String? {
        val v = _toast.value
        _toast.value = null
        return v
    }

    fun publishEngineState(state: String, server: XrayServer?) {
        _engineState.value = state
        _engineServer.value = server
    }

    fun publishNetCondition(condition: NetCondition?) {
        _netCondition.value = condition
    }

    fun reset() {
        _stats.value = TunnelStats()
        _logs.value = emptyList()
        _engineState.value = ""
        _engineServer.value = null
        _netCondition.value = null
    }
}

/**
 * Direct access to the active engine instance for UI commands
 * (speed test, diagnostics) — the counterpart of the desktop window
 * holding its TunnelManager reference.
 */
object EngineBridge {
    @Volatile var manager: ForoughTunnelManager? = null
}
