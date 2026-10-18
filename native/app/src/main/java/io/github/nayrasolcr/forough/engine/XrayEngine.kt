package io.github.nayrasolcr.forough.engine

import android.content.Context
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.net.NetTools
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The second engine — a REAL Xray core (v2.5.0).
 *
 * The official Xray-android binary is bundled by CI as
 * jniLibs/arm64-v8a/libxray.so and executed from the app's native library
 * directory (the one location Android still allows exec from). The engine:
 *
 *  - writes the generated config to filesDir/xray_config.json
 *  - runs `libxray.so run -c <config>` as a child process
 *  - pumps its stdout into the Forough log
 *  - health-probes its HTTP inbound every 15s through NetTools; after two
 *    consecutive failures it ROTATES to the next server (rewrite+restart)
 *    and finally reports exhaustion to the host via the listener.
 *
 * All lifecycle methods are safe to call from any thread.
 */
class XrayEngine(private val context: Context) {

    interface Listener {
        fun onEngineState(state: String, server: XrayServer?)
        fun onServersExhausted()
    }

    var listener: Listener? = null
    var fragmentTls = true

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "forough-xray")
    }
    private val runningFlag = AtomicBoolean(false)
    private var process: Process? = null
    private var servers: List<XrayServer> = emptyList()
    private var activeIndex = 0
    private var probeFailures = 0
    @Volatile private var stopping = false
    @Volatile private var state = "stopped"

    val isRunning: Boolean get() = runningFlag.get()
    val currentState: String get() = state
    val activeServer: XrayServer?
        get() = servers.getOrNull(activeIndex)

    fun available(): Boolean = binaryFile().exists()

    private fun binaryFile(): File {
        val dir = context.applicationInfo.nativeLibraryDir
        return File(dir, "libxray.so")
    }

    /**
     * Start with the given server list at startIndex. Returns false when
     * the engine is unavailable (no bundled binary) or no valid servers.
     */
    @Synchronized
    fun start(serverList: List<XrayServer>, startIndex: Int = 0): Boolean {
        if (runningFlag.get()) stop()
        if (!available()) {
            appendLog("xray engine unavailable — core binary missing for this ABI")
            return false
        }
        val valid = serverList.filter { it.address.isNotBlank() && it.port in 1..65535 }
        if (valid.isEmpty()) {
            appendLog("xray engine: no valid servers")
            return false
        }
        servers = valid
        activeIndex = startIndex.coerceIn(0, valid.size - 1)
        stopping = false
        runningFlag.set(true)
        executor.execute { runLoop() }
        return true
    }

    @Synchronized
    fun stop() {
        stopping = true
        runningFlag.set(false)
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        setState("stopped")
    }

    /** Force-rotate to the next server (user tap or health decision). */
    @Synchronized
    fun rotate(): Boolean {
        if (servers.isEmpty()) return false
        val next = (activeIndex + 1) % servers.size
        if (next == activeIndex) return false
        appendLog("xray engine: rotating to server ${next + 1}/${servers.size} — ${servers[next].name}")
        stopProcessOnly()
        activeIndex = next
        probeFailures = 0
        runningFlag.set(true)
        executor.execute { runLoop() }
        return true
    }

    private fun runLoop() {
        while (runningFlag.get() && !stopping) {
            val server = servers.getOrNull(activeIndex) ?: break
            setState("connecting", server)
            appendLog("xray engine: starting core — ${server.protocolLabel} ${server.address}:${server.port} (${server.name})")

            val config = File(context.filesDir, "xray_config.json")
            try {
                config.writeText(XrayConfigBuilder.build(server, fragmentTls))
            } catch (e: Exception) {
                appendLog("xray engine: config write failed: ${e.message}")
                break
            }

            val proc = try {
                ProcessBuilder(binaryFile().absolutePath, "run", "-c", config.absolutePath, "-format", "json")
                    .redirectErrorStream(true)
                    .start()
            } catch (e: Exception) {
                appendLog("xray engine: exec failed: ${e.message}")
                break
            }
            process = proc

            // Output pump — Xray's log lines land in the Forough log.
            val pump = Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        if (line.isNotBlank()) appendLog("[xray] ${line.take(220)}")
                    }
                } catch (_: Exception) {
                }
            }
            pump.isDaemon = true
            pump.start()

            setState("probing", server)
            var alive = false
            // Give the core up to ~12s for its first successful round trip.
            var waited = 0
            while (runningFlag.get() && !stopping && waited < 12_000) {
                Thread.sleep(2_000)
                waited += 2_000
                val code = NetTools.probeTraffic(XrayConfigBuilder.HTTP_PORT, 6_000)
                if (code in 200..399) {
                    alive = true
                    break
                }
                if (!proc.isAlive) break
            }

            if (alive && runningFlag.get() && !stopping) {
                probeFailures = 0
                setState("online", server)
                appendLog("xray engine: ONLINE via ${server.protocolLabel} ${server.address}")
                watchWhileOnline(proc)
            }

            if (!runningFlag.get() || stopping) break

            // Not alive (or lost): rotate or exhaust.
            appendLog("xray engine: server ${activeIndex + 1}/${servers.size} failed (${server.address})")
            stopProcessOnly()
            val next = activeIndex + 1
            if (next >= servers.size) {
                setState("exhausted", null)
                appendLog("xray engine: all ${servers.size} servers failed")
                try {
                    listener?.onServersExhausted()
                } catch (_: Exception) {
                }
                break
            }
            activeIndex = next
        }
        if (!stopping) setState("stopped")
    }

    /** While online: probe every 15s; two failures rotate the server. */
    private fun watchWhileOnline(proc: Process) {
        val server = servers.getOrNull(activeIndex) ?: return
        setState("online", server)
        while (runningFlag.get() && !stopping && proc.isAlive) {
            Thread.sleep(15_000)
            if (!runningFlag.get() || stopping) break
            if (!proc.isAlive) {
                appendLog("xray engine: core process exited unexpectedly")
                return
            }
            val code = NetTools.probeTraffic(XrayConfigBuilder.HTTP_PORT, 8_000)
            if (code in 200..399) {
                probeFailures = 0
            } else {
                probeFailures++
                appendLog("xray engine: health probe failed ($probeFailures/2)")
                if (probeFailures >= 2) return
            }
        }
    }

    private fun stopProcessOnly() {
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        try {
            // Give the OS a beat to release the ports.
            Thread.sleep(300)
        } catch (_: InterruptedException) {
        }
    }

    private fun setState(s: String, server: XrayServer? = null) {
        state = s
        try {
            listener?.onEngineState(s, server ?: activeServer)
        } catch (_: Exception) {
        }
    }

    private fun appendLog(line: String) {
        ForoughBus.appendLog(line)
    }
}
