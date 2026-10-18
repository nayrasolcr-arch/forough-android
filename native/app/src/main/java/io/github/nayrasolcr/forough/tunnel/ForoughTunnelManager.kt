package io.github.nayrasolcr.forough.tunnel

import android.content.Context
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import ca.psiphon.PsiphonTunnel
import io.github.nayrasolcr.forough.CrashLog
import io.github.nayrasolcr.forough.EngineBridge
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.net.NetTools
import io.github.nayrasolcr.forough.service.ForoughVpnService
import io.github.nayrasolcr.forough.vpn.VpnManager
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The Forough tunnel engine — Android counterpart of the desktop client's
 * TunnelManager (gui/src/services/tunnel-manager.vala).
 *
 * Parity kept with the desktop: honest status chain (connecting → connected
 * → reconnecting), real protocol display via ActiveTunnel notices, live
 * byte rates from BytesTransferred, end-to-end traffic probe through the
 * local HTTP proxy (same endpoints/success rule, first check 6s after
 * start, every 20s, 8s cap), automatic tunnel restart on dead traffic
 * (thresholds 2 before first success / 3 after, max 2 per session), the
 * 8s-after-connect performance probe (TTFB + 10 MB/15s speed test) and
 * manual speed tests on demand.
 *
 * Android-specific: routing is whole-device via VpnService + tun2socks, so
 * the desktop's TUN watchdog / system-proxy fallback layers are structural
 * here — the routing_status row reports the VPN state instead.
 */
class ForoughTunnelManager(
    private val context: Context
) : PsiphonTunnel.HostService {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    // ALL engine lifecycle operations (start / teardown / auto-restart) run
    // serialized on this single thread — a fast disconnect→connect tap can
    // never make start and teardown race each other on the same manager.
    private val lifecycleExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "forough-lifecycle")
    }
    private val vpnManager = VpnManager.getInstance()
    private val parserState = NoticeParser.ParserState()

    private var tunnel: PsiphonTunnel? = null
    private var options = TunnelOptions()

    // Same tunables as the desktop TunnelManager.
    private val trafficCheckFirstDelayMs = 6_000L
    private val trafficCheckIntervalMs = 20_000L
    private val trafficCheckMaxTimeMs = 8_000
    private val trafficFailRestartThreshold = 2
    private val trafficFailRestartThresholdAfterOk = 3
    private val autoRestartMax = 2
    private val perfProbeDelayMs = 8_000L

    // ------------------------------------------------------------------
    // Rescue Engine (v2.4.0) — the "heavy filtering" auto-escalation ladder.
    //
    // Field diagnosis (user log, 2026-10): in CDN fronting mode under a
    // severe block, ALL 1701 CDN scan candidates were dead and QUIC was
    // fully blocked — the core spun forever with 0 tunnels because the
    // protocol set had no escape. The ladder below switches strategies on
    // a timer while nothing establishes, mirroring how the strongest
    // clients (official Psiphon tactics, Chinese-style protocol switching)
    // behave under national-level filtering:
    //
    //   stage 0 — the user's chosen mode, untouched (30s)
    //   stage 1 — TCP-only self-heal: strip QUIC (the log's dominant
    //             failure mode: QUICTransporter.dialQUIC failed x179) (35s)
    //   stage 2 — EXTREME protocol set: every meek form + Shadowsocks +
    //             SSH/OSSH + CDN scan + conduit, region kept (45s)
    //   stage 3 — EXTREME + region preference released, keeps re-arming on
    //             a 45s cadence until connected
    //
    // Any successful connect resets the ladder. A manual reconnect always
    // starts fresh at stage 0. Each escalation is announced in the log and
    // the UI (rescueStage stat) so the user SEES the engine fighting.
    // ------------------------------------------------------------------
    private val rescueStageTimeoutMs = longArrayOf(30_000L, 35_000L, 45_000L, 45_000L)
    private val rescueMaxStage = 3
    @Volatile private var rescueStage = 0
    @Volatile private var rescueStageStartMs = 0L
    private var rescueWatchdogScheduled = false

    companion object {
        private const val RESCUE_TICK_MS = 5_000L
    }

    @Volatile private var running = false
    @Volatile private var stopping = false
    private var trafficCheckScheduled = false
    private var trafficInFlight = false
    private var trafficFailures = 0
    private var trafficEverOk = false
    private var autoRestartCount = 0
    private var perfProbeDone = false
    private var perfProbeInFlight = false
    private var lastPerfReport = ""
    private var lastTrafficCode = 0

    // Bytes arrive several times per second; the UI only needs ~2 updates
    // per second, so the bytes path publishes at most this often.
    private val statsPublishIntervalMs = 500L
    @Volatile private var lastStatsPublishMs = 0L

    private var stats = TunnelStats()
    private val logs = ArrayList<String>()

    val isRunning: Boolean get() = running

    init {
        EngineBridge.manager = this
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Synchronized
    fun start(options: TunnelOptions) {
        if (running) return
        // stop() clears EngineBridge; re-register so a fast disconnect →
        // connect cycle on a reused service instance never leaves the UI
        // without its engine handle (speed test / diagnostics would go dead).
        EngineBridge.manager = this
        this.options = options
        stopping = false
        running = true
        trafficFailures = 0
        trafficEverOk = false
        autoRestartCount = 0
        trafficCheckScheduled = false
        trafficInFlight = false
        perfProbeDone = false
        perfProbeInFlight = false
        lastPerfReport = ""
        lastTrafficCode = 0

        // Rescue Engine: a manual connect always starts a fresh ladder.
        rescueStage = 0
        rescueStageStartMs = System.currentTimeMillis()

        stats = TunnelStats(
            status = "connecting",
            startedAtMs = System.currentTimeMillis()
        )
        publish()
        ForoughBus.publishRunning(true)
        appendLog("connecting — Forough Android (engine: psiphon-tunnel-core)")
        appendLog("rescue engine armed — auto-escalation if the network blocks this mode")

        scheduleRescueWatchdog()

        try {
            vpnManager.registerHostService(builderProvider)
            vpnManager.setShareProxyOnNetwork(options.enableLan)
            vpnManager.setSplitTunnelFilter(options.splitTunnelMode, options.splitTunnelPackages)
            vpnManager.vpnEstablish()

            val newTunnel = PsiphonTunnel.newPsiphonTunnel(this)
            tunnel = newTunnel
            newTunnel.setVpnMode(true)
            // No embedded server entries — the official signed remote server
            // list is used, exactly like the desktop client (Linux parity).
            newTunnel.startTunneling("")
        } catch (e: Exception) {
            appendLog("start failed: ${e.message}")
            ForoughBus.setToast("connect failed: ${e.message}")
            stop()
        }
    }

    /**
     * Non-blocking start request — the service calls this so the heavy
     * engine bring-up (vpnEstablish, startTunneling) never runs on the main
     * thread, and so start serializes with stop/auto-restart on the single
     * lifecycle thread.
     */
    fun startAsync(options: TunnelOptions) {
        lifecycleExecutor.execute {
            try {
                start(options)
            } catch (e: Exception) {
                appendLog("start error: ${e.message}")
                ForoughBus.publishRunning(false)
            }
        }
    }

    /**
     * Fast, non-blocking stop request — the fix for the "disconnect button
     * freezes" symptom. The heavy teardown (tun2socks terminate joins its
     * native loop, tunnel.stop() joins engine threads) used to run on the
     * MAIN thread inside the service; on slow devices that blocked the UI
     * for seconds and felt like the button was stuck.
     *
     * Sequence:
     *   1. immediately flip the UI to disconnected (button unlocks at once)
     *   2. run the heavy teardown on the lifecycle thread (serialized with
     *      start — a fast disconnect→connect tap can never race)
     *   3. invoke onDone on the main thread when it is safe to stopSelf()
     */
    fun requestStop(onDone: () -> Unit) {
        if (!running && tunnel == null && !ForoughBus.running.value) {
            // Nothing alive — honour the callback anyway so the service can
            // still finish its stop flow.
            mainHandler.post(onDone)
            return
        }
        // 1. Instant UI unlock: running=false first so the button flips to
        // "connect" the same frame the user taps it, then the honest
        // "disconnecting" status keeps the notification truthful while the
        // teardown finishes in the background.
        running = false
        stopping = true
        cancelTrafficCheck()
        cancelPerfProbe()
        cancelRescueWatchdog()
        mutate {
            it.status = "disconnecting"
            true
        }
        publish()
        ForoughBus.publishRunning(false)
        appendLog("disconnecting — stopping the tunnel…")

        // 2. Heavy teardown on the lifecycle thread (serialized with start).
        lifecycleExecutor.execute {
            try {
                teardownHeavy()
            } catch (e: Exception) {
                appendLog("teardown error: ${e.message}")
            }
            mainHandler.post {
                // Finalize state regardless of teardown outcome.
                synchronized(this) {
                    tunnel = null
                    vpnManager.unregisterHostService()
                    EngineBridge.manager = null
                    stats = TunnelStats.from(stats) {
                        it.status = "disconnected"
                        it.tunnelCount = 0
                        it.tunnelProtocol = "-"
                        it.beastMode = "-"
                        it.cdnScan = "-"
                        it.trafficStatus = "-"
                        it.routingStatus = "-"
                        it.downstreamBps = 0
                        it.upstreamBps = 0
                        it.uptimeMs = 0
                        it.connectedAtMs = 0
                        it.connectDurationMs = 0
                    }
                    publish()
                }
                appendLog("tunnel stopped")
                // 3. Service can now stopSelf() safely.
                onDone()
            }
        }
    }

    /**
     * The blocking half of a stop: tun2socks termination, engine stop and
     * VPN teardown. Only ever call from a background thread — this can take
     * seconds on slow devices (native joins).
     */
    private fun teardownHeavy() {
        try {
            vpnManager.stopRouteThroughTunnel()
        } catch (_: Exception) {
        }
        try {
            tunnel?.stop()
        } catch (_: Exception) {
        }
        try {
            vpnManager.vpnTeardown()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun stop() {
        // Only skip when nothing was ever started AND the UI isn't showing a
        // running session — otherwise a failed start() would leave the bus
        // stuck on running=true and the connect button would look dead
        // (the "cannot disconnect" symptom).
        if (!running && tunnel == null && !ForoughBus.running.value) {
            return
        }
        stopping = true
        running = false
        cancelTrafficCheck()
        cancelPerfProbe()
        cancelRescueWatchdog()

        teardownHeavy()
        tunnel = null
        vpnManager.unregisterHostService()
        EngineBridge.manager = null

        stats = TunnelStats.from(stats) {
            it.status = "disconnected"
            it.tunnelCount = 0
            it.tunnelProtocol = "-"
            it.beastMode = "-"
            it.cdnScan = "-"
            it.trafficStatus = "-"
            it.routingStatus = "-"
            it.downstreamBps = 0
            it.upstreamBps = 0
            it.uptimeMs = 0
            it.connectedAtMs = 0
            it.connectDurationMs = 0
        }
        publish()
        ForoughBus.publishRunning(false)
        appendLog("tunnel stopped")
    }

    /** Restart the core with the same options (desktop trigger_auto_restart). */
    private fun autoRestart(reason: String) {
        if (stopping || autoRestartCount >= autoRestartMax) {
            if (autoRestartCount >= autoRestartMax) {
                appendLog("restart budget exhausted — tunnel stays up as-is; reconnect manually")
            }
            return
        }
        autoRestartCount++
        appendLog("traffic is not going through — restarting the tunnel automatically " +
            "($autoRestartCount/$autoRestartMax) — $reason")
        ForoughBus.setToast("restart tunnel ($autoRestartCount/$autoRestartMax)")
        stats = TunnelStats.from(stats) {
            it.status = "reconnecting"
            it.trafficStatus = "restarting tunnel…"
        }
        publish()

        // Serialized with start/stop on the lifecycle thread.
        lifecycleExecutor.execute {
            try {
                vpnManager.stopRouteThroughTunnel()
                tunnel?.stop()
            } catch (_: Exception) {
            }
            try {
                vpnManager.vpnTeardown()
            } catch (_: Exception) {
            }

            try {
                vpnManager.registerHostService(builderProvider)
                vpnManager.vpnEstablish()
                val newTunnel = PsiphonTunnel.newPsiphonTunnel(this)
                mainHandler.post {
                    tunnel = newTunnel
                }
                newTunnel.setVpnMode(true)
                newTunnel.startTunneling("")
            } catch (e: Exception) {
                appendLog("auto-restart failed: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Rescue Engine implementation
    // ------------------------------------------------------------------

    private fun scheduleRescueWatchdog() {
        if (rescueWatchdogScheduled || !running) return
        rescueWatchdogScheduled = true
        mainHandler.postDelayed(rescueWatchdogRunnable, RESCUE_TICK_MS)
    }

    private fun cancelRescueWatchdog() {
        rescueWatchdogScheduled = false
        mainHandler.removeCallbacks(rescueWatchdogRunnable)
    }

    private val rescueWatchdogRunnable = object : Runnable {
        override fun run() {
            if (!running || stopping) return
            try {
                rescueWatchdogTick()
            } finally {
                if (running && rescueWatchdogScheduled) {
                    mainHandler.postDelayed(this, RESCUE_TICK_MS)
                }
            }
        }
    }

    /**
     * Watchdog tick: while the session runs but no tunnel has established,
     * escalate through the rescue ladder when the current stage's budget
     * expires. Connected sessions are untouched.
     */
    private fun rescueWatchdogTick() {
        if (!running || stopping) return
        if (stats.status == "connected") return
        if (rescueStageStartMs == 0L) rescueStageStartMs = System.currentTimeMillis()

        val elapsed = System.currentTimeMillis() - rescueStageStartMs
        val budget = rescueStageTimeoutMs[rescueStage.coerceIn(0, rescueStageTimeoutMs.size - 1)]
        if (elapsed < budget) return

        if (rescueStage >= rescueMaxStage) {
            // Ladder is exhausted: keep the last (strongest) configuration
            // running — the core keeps dialing new candidates — and just
            // refresh the stage timer so the badge stays honest.
            rescueStageStartMs = System.currentTimeMillis()
            return
        }
        escalateRescue()
    }

    /** Tear down and re-establish the tunnel one rung up the ladder. */
    private fun escalateRescue() {
        val previous = rescueStage
        rescueStage++
        rescueStageStartMs = System.currentTimeMillis()

        val base = options
        var next: TunnelOptions = when (rescueStage) {
            1 -> base.copy(
                tcpOnlyOverride = true,
                rescueStage = 1
            )
            2 -> base.copy(
                tcpOnlyOverride = false,
                // v2.4.1: jump straight to the EXTREME protocol set while
                // keeping the region preference — the second field log
                // showed direct Psiphon IPs fully blackholed, so inproxy
                // (conduit) and every meek form must be in the pool as
                // early as possible.
                protocol = ProtocolMode.EXTREME,
                rescueStage = 2
            )
            else -> base.copy(
                tcpOnlyOverride = false,
                protocol = ProtocolMode.EXTREME,
                region = "",
                rescueStage = 3
            )
        }
        // Stage 3 releases the region preference: when a country is under a
        // total shutdown, waiting for a specific egress region starves the
        // candidate pool — any working region beats no connection.
        if (rescueStage != 3) {
            next = next.copy(region = base.region)
        }

        options = next
        val stageNote = when (rescueStage) {
            1 -> "QUIC appears blocked — TCP-only protocols"
            2 -> "EXTREME protocol pool — every transport incl. conduit"
            else -> "EXTREME rescue — every transport, region preference released"
        }
        appendLog("rescue engine stage $rescueStage/$rescueMaxStage — $stageNote")
        ForoughBus.setToast("rescue stage $rescueStage: $stageNote")
        mutate {
            it.status = "reconnecting"
            it.trafficStatus = "rescue stage $rescueStage…"
            it.rescueStage = stageBadge(rescueStage, stageNote)
            true
        }
        publish()

        // Serialized with start/stop on the lifecycle thread — same pattern
        // as autoRestart (which stays responsible for dead-traffic restarts
        // AFTER a tunnel exists; this ladder handles never-established).
        lifecycleExecutor.execute {
            if (!running || stopping) return@execute
            try {
                vpnManager.stopRouteThroughTunnel()
                tunnel?.stop()
            } catch (_: Exception) {
            }
            try {
                vpnManager.vpnTeardown()
            } catch (_: Exception) {
            }
            try {
                vpnManager.registerHostService(builderProvider)
                vpnManager.vpnEstablish()
                val newTunnel = PsiphonTunnel.newPsiphonTunnel(this)
                mainHandler.post { tunnel = newTunnel }
                newTunnel.setVpnMode(true)
                newTunnel.startTunneling("")
                appendLog("rescue engine re-established the tunnel core (stage $rescueStage, was $previous)")
            } catch (e: Exception) {
                appendLog("rescue stage $rescueStage restart failed: ${e.message}")
            }
        }
    }

    private fun stageBadge(stage: Int, note: String): String = "$stage/$rescueMaxStage — $note"

    // ------------------------------------------------------------------
    // PsiphonTunnel.HostService
    // ------------------------------------------------------------------

    override fun getContext(): Context = context

    override fun getPsiphonConfig(): String = TunnelConfigBuilder.build(options)

    override fun bindToDevice(fileDescriptor: Long) {
        if (!vpnProtect(fileDescriptor.toInt())) {
            throw RuntimeException("VpnService.protect() failed")
        }
    }

    private fun vpnProtect(fd: Int): Boolean {
        val service = ForoughVpnService.instance ?: return false
        return service.protect(fd)
    }

    private val builderProvider = object : VpnManager.VpnServiceBuilderProvider {
        override fun vpnServiceBuilder(): VpnService.Builder {
            val service = ForoughVpnService.instance
                ?: throw IllegalStateException("VPN service is not running")
            return service.Builder()
        }
    }

    override fun onDiagnosticMessage(message: String) {
        appendLog(message)
        mutate { NoticeParser.apply(message, it, "127.0.0.1", parserState) }
        // The AAR formats diagnostic notices as "Type: {json}" (not raw JSON),
        // so surface Info messages to the beast-mode / CDN-scan parser to
        // keep those stats rows live — desktop parity.
        if (message.startsWith("Info: ")) {
            try {
                val inner = JSONObject(message.removePrefix("Info: ")).optString("message", "")
                if (inner.isNotEmpty()) {
                    mutate { NoticeParser.applyDiagnosticLine(inner, it) }
                }
            } catch (_: Exception) {
            }
        }
        maybeScheduleAfterConnect()
    }

    override fun onBytesTransferred(sent: Long, received: Long) {
        mutate {
            it.totalDownBytes += received
            it.totalUpBytes += sent
            val now = System.currentTimeMillis()
            if (parserState.lastBytesTimeMs > 0 && (received > 0 || sent > 0)) {
                val elapsedSec = (now - parserState.lastBytesTimeMs) / 1000.0
                if (elapsedSec > 0) {
                    it.downstreamBps = (received / elapsedSec + 0.5).toLong()
                    it.upstreamBps = (sent / elapsedSec + 0.5).toLong()
                }
            }
            parserState.lastBytesTimeMs = now
            true
        }
        // Bytes are the only continuously-changing stats — without this
        // throttled publish the traffic row and graph would freeze until the
        // next lifecycle event (the "traffic not displayed" symptom).
        val now = System.currentTimeMillis()
        if (now - lastStatsPublishMs >= statsPublishIntervalMs) {
            lastStatsPublishMs = now
            publish()
        }
    }

    override fun onListeningSocksProxyPort(port: Int) {
        // Start whole-device routing as soon as the core's SOCKS listener is
        // up, so traffic flows the moment the tunnel establishes (and on
        // every automatic reconnect).
        appendLog("SOCKS proxy listening on 127.0.0.1:$port")
        executor.execute {
            try {
                vpnManager.routeThroughTunnel(port)
            } catch (e: Exception) {
                appendLog("routing failed: ${e.message}")
            }
        }
    }

    override fun onListeningHttpProxyPort(port: Int) {
        appendLog("HTTP proxy listening on 127.0.0.1:$port")
    }

    override fun onConnecting() {
        mutate {
            it.status = if (it.status == "connected") "reconnecting" else "connecting"
            true
        }
        publish()
    }

    override fun onConnected() {
        // The ladder did its job — disarm and clear the stage badge.
        if (rescueStage > 0) {
            appendLog("rescue engine standing down — tunnel established at stage $rescueStage")
        }
        rescueStage = 0
        cancelRescueWatchdog()
        mutate {
            it.status = "connected"
            it.tunnelCount = 1
            it.publicIp = "-"
            it.rescueStage = "-"
            if (it.connectedAtMs == 0L) it.connectedAtMs = System.currentTimeMillis()
            true
        }
        publish()
        scheduleTrafficCheck()
        schedulePerfProbe()
        // Fill the Public IP row through the tunnel (desktop IpService parity).
        fetchPublicIp()
    }

    /** Unix ms when the current session's tunnel connected (notification chronometer). */
    fun connectedAtMs(): Long = stats.connectedAtMs

    override fun onAvailableEgressRegions(regions: MutableList<String>?) {
        mutate {
            it.availableRegions = regions?.size ?: 0
            true
        }
        publish()
    }

    override fun onClientRegion(region: String?) {
        mutate {
            it.clientRegion = region ?: "-"
            true
        }
        publish()
    }

    override fun onConnectedServerRegion(region: String?) {
        mutate {
            it.egressRegion = region ?: "-"
            true
        }
        publish()
    }

    override fun onTrafficRateLimits(upstreamBytesPerSecond: Long, downstreamBytesPerSecond: Long) {
        // The core reports -1 for "unlimited" — that must never clobber the
        // real measured rates from the BytesTransferred deltas.
        if (upstreamBytesPerSecond < 0 || downstreamBytesPerSecond < 0) return
        mutate {
            it.downstreamBps = downstreamBytesPerSecond
            it.upstreamBps = upstreamBytesPerSecond
            true
        }
        publish()
    }

    override fun onUpstreamProxyError(message: String?) {
        appendLog("upstream proxy error: ${message ?: "unknown"}")
    }

    override fun onExiting() {
        mutate { it.status = "exiting"; true }
        publish()
    }

    override fun onSocksProxyPortInUse(port: Int) {
        appendLog("SOCKS proxy port $port already in use")
    }

    override fun onHttpProxyPortInUse(port: Int) {
        appendLog("HTTP proxy port $port already in use")
    }

    // ------------------------------------------------------------------
    // Scheduling helpers
    // ------------------------------------------------------------------

    private fun maybeScheduleAfterConnect() {
        if (stats.status == "connected") {
            scheduleTrafficCheck()
            schedulePerfProbe()
        }
    }

    private fun scheduleTrafficCheck() {
        if (trafficCheckScheduled || !running) return
        trafficCheckScheduled = true
        mainHandler.postDelayed(trafficCheckRunnable, trafficCheckFirstDelayMs)
    }

    private fun cancelTrafficCheck() {
        trafficCheckScheduled = false
        trafficInFlight = false
        mainHandler.removeCallbacks(trafficCheckRunnable)
    }

    private val trafficCheckRunnable = object : Runnable {
        override fun run() {
            if (!running || stopping) return
            if (stats.status == "connected") {
                runTrafficCheck()
            }
            if (running) {
                mainHandler.postDelayed(this, trafficCheckIntervalMs)
            }
        }
    }

    private fun runTrafficCheck() {
        if (trafficInFlight) return
        trafficInFlight = true
        mutate { it.trafficStatus = "checking traffic…"; true }
        publish()

        executor.execute {
            val code = NetTools.probeTraffic(8081, trafficCheckMaxTimeMs)
            mainHandler.post { trafficCheckDone(code) }
        }
    }

    private fun trafficCheckDone(code: Int) {
        trafficInFlight = false
        lastTrafficCode = code
        val ok = code in 200..399

        if (!running || stopping) return

        if (ok) {
            trafficFailures = 0
            trafficEverOk = true
            mutate { it.trafficStatus = "online"; true }
        } else {
            trafficFailures++
            val threshold =
                if (trafficEverOk) trafficFailRestartThresholdAfterOk else trafficFailRestartThreshold
            mutate { it.trafficStatus = "no traffic"; true }
            if (trafficFailures >= threshold) {
                trafficFailures = 0
                autoRestart("probe HTTP 0 (all endpoints failed)")
                return
            }
        }
        publish()
    }

    // ------------------------------------------------------------------
    // Performance probe (desktop run_perf_probe parity)
    // ------------------------------------------------------------------

    private val perfProbeRunnable = Runnable {
        if (running && stats.status == "connected" && !perfProbeDone) {
            runSpeedTest(manual = false)
        }
    }

    private fun schedulePerfProbe() {
        if (perfProbeDone || perfProbeInFlight || !running) return
        mainHandler.removeCallbacks(perfProbeRunnable)
        mainHandler.postDelayed(perfProbeRunnable, perfProbeDelayMs)
    }

    private fun cancelPerfProbe() {
        perfProbeInFlight = false
        mainHandler.removeCallbacks(perfProbeRunnable)
    }

    /** Manual or automatic speed test — result lands in logs + diagnostics. */
    fun runSpeedTest(manual: Boolean) {
        if (!running || stats.status != "connected" || perfProbeInFlight) return
        if (manual) perfProbeDone = false
        perfProbeInFlight = true
        appendLog(if (manual) "speed test running — numbers land in the log and diagnostics" else "performance probe running")
        executor.execute {
            val ttfb = NetTools.probeTtfb()
            val speed = NetTools.probeSpeed()
            perfProbeInFlight = false
            if (!perfProbeDone) perfProbeDone = true
            lastPerfReport = ttfb.report() + "\n" + speed.report()
            appendLog(lastPerfReport)
            if (manual) {
                // Surface the headline number directly in the UI so the tap
                // on "speed test" always produces a visible, immediate answer.
                ForoughBus.setToast(speed.report())
            }
        }
    }

    // ------------------------------------------------------------------
    // IP / diagnostics
    // ------------------------------------------------------------------

    fun fetchPublicIp() {
        if (stats.status != "connected") return
        executor.execute {
            val result = NetTools.fetchPublicIp()
            if (result != null) {
                mutate {
                    it.publicIp = result.first
                    true
                }
                publish()
            }
        }
    }

    fun buildDiagnosticsReport(trigger: String): String {
        val s = stats
        val sb = StringBuilder()
        sb.appendLine("Forough Android diagnostics ($trigger)")
        sb.appendLine("version: ${io.github.nayrasolcr.forough.BuildConfig.VERSION_NAME}")
        sb.appendLine("engine: psiphon-tunnel-core (Forough fork, quality-ranked CDN edges)")
        sb.appendLine("status: ${s.status}")
        sb.appendLine("protocol: ${s.tunnelProtocol}")
        sb.appendLine("selected_protocol_mode: ${options.protocol.configValue}")
        sb.appendLine("egress_region_pref: '${options.region}' (connected: ${s.egressRegion})")
        sb.appendLine("client_region: ${s.clientRegion}")
        sb.appendLine("public_ip: ${s.publicIp}")
        sb.appendLine("beast_mode: ${options.enableBeastMode} (${s.beastMode})")
        sb.appendLine("cdn_scan: ${s.cdnScan}")
        sb.appendLine("traffic_status: ${s.trafficStatus} (last probe code $lastTrafficCode)")
        sb.appendLine("rescue_engine: stage $rescueStage/$rescueMaxStage (badge: ${s.rescueStage})")
        sb.appendLine("proxies: http ${s.httpProxy}, socks ${s.socksProxy}")
        sb.appendLine("routing: VpnService whole-device (tun2socks)")
        sb.appendLine("traffic: down ${NetTools.humanBytes(s.totalDownBytes)} / up ${NetTools.humanBytes(s.totalUpBytes)}")
        sb.appendLine("auto_restarts: $autoRestartCount/$autoRestartMax")
        if (lastPerfReport.isNotEmpty()) {
            sb.appendLine("perf_probe:")
            sb.appendLine(lastPerfReport)
        } else {
            sb.appendLine("perf_probe: (not run this session)")
        }
        sb.appendLine("log tail:")
        synchronized(logs) {
            for (line in logs.takeLast(40)) {
                sb.appendLine("  $line")
            }
        }
        // Last session's uncaught exception (crash journal) — the real data
        // a user can share when something crashes on their device.
        CrashLog.read(context)?.let { crash ->
            sb.appendLine()
            sb.appendLine("last crash:")
            sb.appendLine(crash)
        }
        return sb.toString()
    }

    fun logText(): String = synchronized(logs) { logs.joinToString("\n") }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun mutate(block: (TunnelStats.Mutable) -> Boolean) {
        synchronized(this) {
            val m = TunnelStats.Mutable(stats)
            if (block(m)) {
                stats = m.build()
            }
        }
    }

    private fun publish() {
        val snapshot = synchronized(this) { stats }
        // Keep the uptime row live like the desktop stats timer.
        if (snapshot.status == "connected" && snapshot.connectedAtMs > 0) {
            ForoughBus.publishStats(
                snapshot.copy(
                    uptimeMs = System.currentTimeMillis() - snapshot.startedAtMs,
                    connectDurationMs = System.currentTimeMillis() - snapshot.connectedAtMs
                )
            )
        } else {
            ForoughBus.publishStats(snapshot)
        }
    }

    private fun appendLog(line: String) {
        ForoughBus.appendLog(line)
        synchronized(logs) {
            logs.add(line)
            if (logs.size > 800) {
                logs.subList(0, logs.size - 700).clear()
            }
        }
    }
}
