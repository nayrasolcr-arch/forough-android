package io.github.nayrasolcr.forough.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Xray config builder (v2.5.0 second engine).
 *
 * Produces the Xray-core JSON for the Forough client:
 *
 *  - SOCKS inbound on 127.0.0.1:10808 (tun2socks routes the VPN here)
 *  - HTTP inbound on 127.0.0.1:10882 (health probe reuses NetTools)
 *  - ONE active outbound (the selected server) — rotation = rewrite+restart
 *  - **Fragment outbound** for every TLS/REALITY server: the freedom
 *    "fragment" dialer splits the TLS ClientHello into small chunks with
 *    delays — the exact technique Chinese users (v2rayN "fragment") and
 *    Iranian users rely on to defeat SNI-based RST injection.
 *
 * Supported transports: raw/tcp, ws, grpc, httpupgrade · security:
 * none, tls, reality (XTLS Vision flow included).
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10882

    fun build(server: XrayServer, fragmentTls: Boolean = true): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // ----- inbounds -----
        val inbounds = JSONArray()
        inbounds.put(JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", SOCKS_PORT)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true))
            .put("sniffing", JSONObject()
                .put("enabled", true)
                .put("destOverride", JSONArray(listOf("http", "tls", "quic"))))
        )
        inbounds.put(JSONObject()
            .put("tag", "http-in")
            .put("listen", "127.0.0.1")
            .put("port", HTTP_PORT)
            .put("protocol", "http")
            .put("settings", JSONObject())
        )
        root.put("inbounds", inbounds)

        // ----- outbounds -----
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(server, fragmentTls))
        if (fragmentTls && (server.security == "tls" || server.security == "reality")) {
            outbounds.put(buildFragmentOutbound())
        }
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        root.put("outbounds", outbounds)
        return root.toString()
    }

    private fun buildProxyOutbound(server: XrayServer, fragmentTls: Boolean): JSONObject {
        val outbound = JSONObject()
        val settings = JSONObject()

        when (server.kind) {
            "vless" -> {
                outbound.put("protocol", "vless")
                val user = JSONObject()
                    .put("id", server.id)
                    .put("encryption", "none")
                if (server.flow.isNotEmpty() &&
                    server.security == "reality" && server.network == "tcp") {
                    user.put("flow", server.flow)
                }
                settings.put("vnext", JSONArray().put(JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("users", JSONArray().put(user))))
            }
            "vmess" -> {
                outbound.put("protocol", "vmess")
                val user = JSONObject()
                    .put("id", server.id)
                    .put("alterId", server.alterId)
                    .put("security", "auto")
                settings.put("vnext", JSONArray().put(JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("users", JSONArray().put(user))))
            }
            "trojan" -> {
                outbound.put("protocol", "trojan")
                settings.put("servers", JSONArray().put(JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("password", server.id)))
            }
            else -> { // shadowsocks
                outbound.put("protocol", "shadowsocks")
                settings.put("servers", JSONArray().put(JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("method", server.ssMethod.ifBlank { "aes-256-gcm" })
                    .put("password", server.id)
                    .put("uot", true)))
            }
        }
        outbound.put("settings", settings)

        // ----- streamSettings -----
        val stream = JSONObject().put("network", server.network)
        when (server.security) {
            "tls" -> {
                stream.put("security", "tls")
                val tls = JSONObject()
                if (server.sni.isNotEmpty()) tls.put("serverName", server.sni)
                tls.put("fingerprint", server.fingerprint.ifBlank { "chrome" })
                tls.put("allowInsecure", false)
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                stream.put("security", "reality")
                val reality = JSONObject()
                if (server.sni.isNotEmpty()) reality.put("serverName", server.sni)
                reality.put("fingerprint", server.fingerprint.ifBlank { "chrome" })
                if (server.publicKey.isNotEmpty()) reality.put("publicKey", server.publicKey)
                if (server.shortId.isNotEmpty()) reality.put("shortId", server.shortId)
                reality.put("show", false)
                stream.put("realitySettings", reality)
            }
            else -> stream.put("security", "none")
        }
        when (server.network) {
            "ws" -> {
                val ws = JSONObject()
                if (server.wsPath.isNotEmpty()) ws.put("path", server.wsPath)
                if (server.wsHost.isNotEmpty()) {
                    ws.put("headers", JSONObject().put("Host", server.wsHost))
                }
                stream.put("wsSettings", ws)
            }
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", server.wsPath.trimStart('/'))
                    .put("multiMode", false))
            "httpupgrade" -> {
                val hu = JSONObject()
                if (server.wsPath.isNotEmpty()) hu.put("path", server.wsPath)
                if (server.wsHost.isNotEmpty()) hu.put("host", server.wsHost)
                stream.put("httpupgradeSettings", hu)
            }
        }
        if (fragmentTls && (server.security == "tls" || server.security == "reality")) {
            // Route this outbound's dials through the fragment dialer —
            // the ClientHello leaves in small, randomly-delayed chunks.
            stream.put("sockopt", JSONObject().put("dialerProxy", "fragment"))
        }
        outbound.put("streamSettings", stream)
        return outbound
    }

    /**
     * The anti-DPI dialer: splits the first TLS client hello packets
     * ("tlshello") into 100-200 byte chunks with 10-20ms gaps. This is the
     * same "fragment" configuration v2rayN users run in China and Iran.
     */
    private fun buildFragmentOutbound(): JSONObject {
        return JSONObject()
            .put("tag", "fragment")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("fragment", JSONObject()
                .put("packets", "tlshello")
                .put("length", "100-200")
                .put("interval", "10-20")))
    }
}
