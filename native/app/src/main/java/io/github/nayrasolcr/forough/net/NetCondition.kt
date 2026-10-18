package io.github.nayrasolcr.forough.net

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Smart internet-condition detection (v2.5.0) — a quick, honest classifier
 * that runs BEFORE and DURING connection attempts and tells the user (and
 * the engine orchestrator) what kind of censorship environment the device
 * is in right now:
 *
 *   OPEN      — international HTTP reachable, direct TCP works
 *   FILTERED  — TCP to foreign hosts works but international HTTP is
 *               blocked/reset (classic DPI filtering)
 *   SHUTDOWN  — even plain TCP to foreign IPs fails (blackhole /
 *               total shutdown conditions)
 *
 * The result drives the auto-engine choice: SHUTDOWN + configured Xray
 * servers → the Xray engine gets tried first; FILTERED → the Psiphon CDN
 * ladder gets a head start; OPEN → everything works, whatever is fastest.
 */
data class NetCondition(
    val label: String,
    val directTcpOk: Boolean,
    val directTcpMs: Long,
    val httpOk: Boolean,
    val httpMs: Long,
    val httpCode: Int
) {
    val isShutdownLike: Boolean get() = label == LABEL_SHUTDOWN
    val isFiltered: Boolean get() = label == LABEL_FILTERED

    companion object {
        const val LABEL_OPEN = "open"
        const val LABEL_FILTERED = "filtered"
        const val LABEL_SHUTDOWN = "shutdown"

        /** Blocking probe — a few seconds total, run on a worker thread. */
        fun diagnose(): NetCondition {
            // 1) Direct TCP to a foreign anycast IP (Cloudflare DNS).
            var tcpOk = false
            var tcpMs = -1L
            try {
                val t0 = System.nanoTime()
                Socket().use { s ->
                    s.connect(InetSocketAddress("1.1.1.1", 443), 3_000)
                    tcpOk = true
                    tcpMs = (System.nanoTime() - t0) / 1_000_000
                }
            } catch (_: Exception) {
            }

            // 2) Direct international HTTP (no proxy) — the gstatic 204.
            var httpOk = false
            var httpMs = -1L
            var code = 0
            try {
                val t0 = System.nanoTime()
                val conn = URL("http://connectivitycheck.gstatic.com/generate_204")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                code = try { conn.responseCode } catch (_: Exception) { 0 }
                httpMs = (System.nanoTime() - t0) / 1_000_000
                httpOk = code in 200..399
                conn.disconnect()
            } catch (_: Exception) {
            }

            val label = when {
                tcpOk && httpOk -> LABEL_OPEN
                tcpOk && !httpOk -> LABEL_FILTERED
                else -> LABEL_SHUTDOWN
            }
            return NetCondition(label, tcpOk, tcpMs, httpOk, httpMs, code)
        }

        /** One-line human summary for the log / UI chip. */
        fun NetCondition.summary(): String = when (label) {
            LABEL_OPEN -> "direct internet OK (tcp ${directTcpMs}ms, http $httpCode)"
            LABEL_FILTERED -> "filtered network (tcp ${directTcpMs}ms ok, http blocked)"
            else -> "shutdown-like network (direct tcp failed)"
        }
    }
}
