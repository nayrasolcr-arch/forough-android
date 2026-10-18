package io.github.nayrasolcr.forough.tunnel

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists TunnelOptions — the Android counterpart of the Linux client's
 * SettingsService + GSettings storage.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("forough_settings", Context.MODE_PRIVATE)

    /** "system" | "fa" | "en" — same surface as the desktop language setting. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Start the tunnel as soon as the app opens (power feature). */
    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    /** Vibrate on connect / disconnect / button taps (power feature). */
    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /**
     * Proxy sharing (LAN / hotspot) quick access — mirrors
     * TunnelOptions.enableLan so screens can flip it without round-tripping
     * a full options save.
     */
    var enableLan: Boolean
        get() = prefs.getBoolean(KEY_LAN, false)
        set(value) = prefs.edit().putBoolean(KEY_LAN, value).apply()

    /** Split tunneling: "off" | "allow" | "exclude" (power feature). */
    var splitTunnelMode: String
        get() = prefs.getString(KEY_SPLIT_MODE, SPLIT_OFF) ?: SPLIT_OFF
        set(value) = prefs.edit().putString(KEY_SPLIT_MODE, value).apply()

    /** Packages for split tunneling — stored as a JSON array (stable iteration order). */
    var splitTunnelPackages: List<String>
        get() = try {
            val arr = JSONArray(prefs.getString(KEY_SPLIT_PACKAGES, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
        set(value) = prefs.edit()
            .putString(KEY_SPLIT_PACKAGES, JSONArray(value).toString())
            .apply()

    // ------------------------------------------------------------------
    // Second engine (v2.5.0) — Xray
    // ------------------------------------------------------------------

    /** "auto" | "psiphon" | "xray" — which engine carries the VPN. */
    var engineMode: String
        get() = prefs.getString(KEY_ENGINE_MODE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_ENGINE_MODE, value).apply()

    /** Xray server share links (vless/vmess/trojan/ss) — one per line, JSON array. */
    var xrayServerLinks: List<String>
        get() = try {
            val arr = JSONArray(prefs.getString(KEY_XRAY_SERVERS, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
        set(value) = prefs.edit()
            .putString(KEY_XRAY_SERVERS, JSONArray(value.distinct()).toString())
            .apply()

    /** Index of the Xray server that should dial first. */
    var xrayActiveIndex: Int
        get() = prefs.getInt(KEY_XRAY_ACTIVE, 0)
        set(value) = prefs.edit().putInt(KEY_XRAY_ACTIVE, value).apply()

    /** Fragment the TLS ClientHello on Xray TLS/REALITY dials (anti-DPI). */
    var xrayFragmentTls: Boolean
        get() = prefs.getBoolean(KEY_XRAY_FRAGMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_XRAY_FRAGMENT, value).apply()

    fun load(): TunnelOptions {
        migrateCdnDefault()
        migrateAutoSpeedDefault()
        return TunnelOptions(
            protocol = ProtocolMode.fromConfig(prefs.getString(KEY_PROTOCOL, "auto") ?: "auto"),
            region = prefs.getString(KEY_REGION, "") ?: "",
            upstreamProxyUrl = prefs.getString(KEY_UPSTREAM, "") ?: "",
            enableTimeout = prefs.getBoolean(KEY_TIMEOUT, false),
            enableLan = prefs.getBoolean(KEY_LAN, false),
            enableBeastMode = prefs.getBoolean(KEY_BEAST, true),
            connectionWorkers = prefs.getInt(KEY_WORKERS, 64),
            enableTurboNetwork = prefs.getBoolean(KEY_TURBO, true),
            inproxyCompartmentId = prefs.getString(KEY_COMPARTMENT, "") ?: "",
            cdnFrontingIpList = prefs.getString(KEY_CDN_IPS, "") ?: "",
            cdnFrontingSni = prefs.getString(KEY_CDN_SNI, "") ?: "",
            oslUrls = prefs.getString(KEY_OSL, "") ?: "",
            splitTunnelMode = splitTunnelMode,
            splitTunnelPackages = splitTunnelPackages
        )
    }

    /**
     * One-time migration: the client's factory default mode is now CDN
     * fronting (the measured-speed mode). Previously-stored "auto" values
     * move to cdn_fronting once; users can still pick another mode in
     * settings afterwards.
     */
    private fun migrateCdnDefault() {
        if (prefs.getBoolean(KEY_MIGRATED_CDN, false)) return
        prefs.edit()
            .putBoolean(KEY_MIGRATED_CDN, true)
            .putString(KEY_PROTOCOL, ProtocolMode.CDN_FRONTING.configValue)
            .apply()
    }

    /**
     * One-time migration (v2.2.0 measured-speed default): CDN-fronting-only
     * mode pins every dial to meek-over-CDN edges, which on many mobile
     * networks throttles the session to kilobytes. The wider AUTO mode keeps
     * all the CDN fronting machinery (scan spec + edge overrides are applied
     * in AUTO too by the config builder) but lets the fast direct protocols
     * (OSSH / QUIC-OSSH / Shadowsocks) win establishment when the network
     * allows them — measured speed goes from KB/s to MB/s.
     */
    private fun migrateAutoSpeedDefault() {
        if (prefs.getBoolean(KEY_MIGRATED_AUTO_SPEED, false)) return
        prefs.edit()
            .putBoolean(KEY_MIGRATED_AUTO_SPEED, true)
            .putString(KEY_PROTOCOL, ProtocolMode.AUTO.configValue)
            .apply()
    }

    fun save(options: TunnelOptions) {
        prefs.edit()
            .putString(KEY_PROTOCOL, options.protocol.configValue)
            .putString(KEY_REGION, options.region)
            .putString(KEY_UPSTREAM, options.upstreamProxyUrl)
            .putBoolean(KEY_TIMEOUT, options.enableTimeout)
            .putBoolean(KEY_LAN, options.enableLan)
            .putBoolean(KEY_BEAST, options.enableBeastMode)
            .putInt(KEY_WORKERS, options.connectionWorkers)
            .putBoolean(KEY_TURBO, options.enableTurboNetwork)
            .putString(KEY_COMPARTMENT, options.inproxyCompartmentId)
            .putString(KEY_CDN_IPS, options.cdnFrontingIpList)
            .putString(KEY_CDN_SNI, options.cdnFrontingSni)
            .putString(KEY_OSL, options.oslUrls)
            .putString(KEY_SPLIT_MODE, options.splitTunnelMode)
            .putString(KEY_SPLIT_PACKAGES, JSONArray(options.splitTunnelPackages).toString())
            .apply()
    }

    /** Scanner scope switches (same surface as the desktop scanner dialog). */
    var scanCloudflare: Boolean
        get() = prefs.getBoolean(KEY_SCAN_CF, true)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_CF, value).apply()

    var scanFastly: Boolean
        get() = prefs.getBoolean(KEY_SCAN_FASTLY, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_FASTLY, value).apply()

    var scanAkamai: Boolean
        get() = prefs.getBoolean(KEY_SCAN_AKAMAI, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_AKAMAI, value).apply()

    var scanCloudfront: Boolean
        get() = prefs.getBoolean(KEY_SCAN_CFRT, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_CFRT, value).apply()

    var scanBunny: Boolean
        get() = prefs.getBoolean(KEY_SCAN_BUNNY, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_BUNNY, value).apply()

    var scanSampleCount: Int
        get() = prefs.getInt(KEY_SCAN_SAMPLE, 1024)
        set(value) = prefs.edit().putInt(KEY_SCAN_SAMPLE, value).apply()

    var scanConcurrency: Int
        get() = prefs.getInt(KEY_SCAN_CONC, 96)
        set(value) = prefs.edit().putInt(KEY_SCAN_CONC, value).apply()

    var scanCustomCidrs: String
        get() = prefs.getString(KEY_SCAN_CIDRS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCAN_CIDRS, value).apply()

    var scanCustomSnis: String
        get() = prefs.getString(KEY_SCAN_SNIS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCAN_SNIS, value).apply()

    companion object {
        const val SPLIT_OFF = "off"
        const val SPLIT_ALLOW = "allow"
        const val SPLIT_EXCLUDE = "exclude"

        private const val KEY_LANGUAGE = "language"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_MIGRATED_CDN = "migrated_cdn_default"
        private const val KEY_MIGRATED_AUTO_SPEED = "migrated_auto_speed_default"
        private const val KEY_REGION = "region"
        private const val KEY_UPSTREAM = "upstream_proxy_url"
        private const val KEY_TIMEOUT = "enable_timeout"
        private const val KEY_LAN = "enable_lan"
        private const val KEY_BEAST = "enable_beast_mode"
        private const val KEY_WORKERS = "connection_workers"
        private const val KEY_TURBO = "enable_turbo_network"
        private const val KEY_COMPARTMENT = "inproxy_compartment_id"
        private const val KEY_CDN_IPS = "cdn_fronting_ip_list"
        private const val KEY_CDN_SNI = "cdn_fronting_sni"
        private const val KEY_OSL = "osl_urls"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_SPLIT_MODE = "split_tunnel_mode"
        private const val KEY_SPLIT_PACKAGES = "split_tunnel_packages"
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_XRAY_SERVERS = "xray_server_links"
        private const val KEY_XRAY_ACTIVE = "xray_active_index"
        private const val KEY_XRAY_FRAGMENT = "xray_fragment_tls"
        private const val KEY_SCAN_CF = "scan_cloudflare"
        private const val KEY_SCAN_FASTLY = "scan_fastly"
        private const val KEY_SCAN_AKAMAI = "scan_akamai"
        private const val KEY_SCAN_CFRT = "scan_cloudfront"
        private const val KEY_SCAN_BUNNY = "scan_bunny"
        private const val KEY_SCAN_SAMPLE = "scan_sample_count"
        private const val KEY_SCAN_CONC = "scan_concurrency"
        private const val KEY_SCAN_CIDRS = "scan_custom_cidrs"
        private const val KEY_SCAN_SNIS = "scan_custom_snis"
    }
}

/** JSON helpers shared by the config builder and diagnostics. */
object JsonUtil {
    fun stringArray(values: List<String>): JSONArray {
        val array = JSONArray()
        for (v in values) array.put(v)
        return array
    }

    fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in pairs) if (v != null) o.put(k, v)
        return o
    }
}
