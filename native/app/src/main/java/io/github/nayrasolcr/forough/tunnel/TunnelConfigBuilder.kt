package io.github.nayrasolcr.forough.tunnel

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tunnel config builder — faithful port of the Forough Linux client's
 * ConfigService.build_config (gui/src/services/config-service.vala) on top
 * of the same base psiphon.config values shipped with the desktop packages
 * (configs/psiphon.config).
 *
 * Every knob the desktop applies is applied here with identical semantics:
 * EgressRegion, EmitBytesTransferred, EmitDiagnosticNotices (required for
 * the real "ActiveTunnel" protocol display), EmitServerAlerts, DNS resolver
 * alternates, timeout lambda, beast mode (AggressiveEstablishment), protocol
 * limiting per mode with the TCP-only self-heal, CDN fronting dial
 * overrides + scan spec (identical edge IP lists), upstream proxy, and OSL
 * root URLs.
 *
 * v2.4.0 engine hardening (user log diagnosis — 0 tunnels established,
 * 1701 CDN scan attempts all dead, QUIC fully blocked):
 *  - CDN_FRONTING mode now carries a fallback protocol tail so a dead
 *    fronting network can still establish a tunnel.
 *  - Tactics stay enabled — the network's remote-tuned parameters are the
 *    mid-session adaptation mechanism (DisableTactics removed).
 *  - EXTREME mode: every transport + conduit + CDN scan for total-shutdown
 *    conditions.
 *  - ConnectionWorkerPoolSize always applied (>= 64, up to 256).
 */
object TunnelConfigBuilder {

    // Base psiphon.config — identical to the desktop client's packaged asset.
    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"
    private const val REMOTE_SERVER_LIST_URL =
        "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"
    private const val REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq" +
        "/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq4" +
        "7Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7W" +
        "fXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSj" +
        "Q3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTji" +
        "jhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHij" +
        "T8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kct" +
        "OfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/Ytl" +
        "wjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqR" +
        "Vfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoA" +
        "UBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="
    private const val REMOTE_SERVER_LIST_FILENAME = "remote_server_list"

    const val HTTP_PROXY_PORT = 8081
    const val SOCKS_PROXY_PORT = 1081

    // Protocol lists — identical to the Linux client's Constants
    // (which mirror the Psiphon Android client's own protocol sets).
    private val DIRECT_PROTOCOLS = listOf(
        "SSH", "OSSH", "TLS-OSSH", "UNFRONTED-MEEK-OSSH",
        "UNFRONTED-MEEK-HTTPS-OSSH", "UNFRONTED-MEEK-SESSION-TICKET-OSSH",
        "QUIC-OSSH", "SHADOWSOCKS-OSSH", "FRONTED-MEEK-OSSH",
        "FRONTED-MEEK-CDN-OSSH", "FRONTED-MEEK-HTTP-OSSH",
        "FRONTED-MEEK-CDN-HTTP-OSSH", "FRONTED-MEEK-QUIC-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH"
    )

    private val CONDUIT_PROTOCOLS = listOf(
        "INPROXY-WEBRTC-SSH", "INPROXY-WEBRTC-OSSH", "INPROXY-WEBRTC-TLS-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-HTTPS-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-SESSION-TICKET-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-HTTP-OSSH",
        "INPROXY-WEBRTC-QUIC-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-QUIC-OSSH",
        "INPROXY-WEBRTC-SHADOWSOCKS-OSSH"
    )

    private val CDN_FRONTING_PROTOCOLS = listOf(
        "FRONTED-MEEK-CDN-OSSH", "FRONTED-MEEK-CDN-HTTP-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH",
        // Native per-server fronting (v2.4.1 — promoted above the
        // unfronted transports): these dial the fronting domain each server
        // entry carries — the pairs the Psiphon network actively rotates
        // and maintains — instead of the baked-in scan corpus. When Iran
        // rotates its blocks faster than the scan corpus updates, these
        // native pairs are the ones most likely to still be alive.
        "FRONTED-MEEK-OSSH", "FRONTED-MEEK-HTTP-OSSH",
        "FRONTED-MEEK-QUIC-OSSH",
        // Fallback tail (v2.4.0 field fix): the user log proved that under
        // heavy filtering every CDN meek front can die (0/1701 scan
        // candidates working). A CDN-only protocol set then spins forever
        // with zero tunnels. These rescue protocols sit AFTER the CDN set —
        // the core still prefers the quality-ranked CDN edges, but when
        // they are all dead it can now actually establish something
        // instead of waiting indefinitely. UNFRONTED-MEEK dials Psiphon
        // server IPs (often blackholed) so it stays behind the native
        // fronting protocols.
        "UNFRONTED-MEEK-SESSION-TICKET-OSSH", "UNFRONTED-MEEK-HTTPS-OSSH",
        "UNFRONTED-MEEK-OSSH",
        "SHADOWSOCKS-OSSH", "TLS-OSSH", "OSSH", "SSH",
        "QUIC-OSSH"
    )

    // EXTREME mode (v2.4.0): every transport the core knows — meek in all
    // forms (CDN-fronted, unfronted, HTTP), QUIC, Shadowsocks, plain
    // SSH/OSSH — plus the conduit (inproxy/WebRTC) family and the full
    // rescue tail. This is the "heavy censorship / total shutdown" mode:
    // maximum protocol diversity, no restrictions. Inproxy candidates that
    // cannot initialize on a given device simply never win; the rest of
    // the pool still dials.
    private val EXTREME_PROTOCOLS: List<String> =
        (DIRECT_PROTOCOLS + CDN_FRONTING_PROTOCOLS + CONDUIT_PROTOCOLS).distinct()

    fun build(options: TunnelOptions): String {
        val root = JSONObject()

        // ----- base psiphon.config -----
        root.put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
        root.put("SponsorId", SPONSOR_ID)
        root.put("RemoteServerListUrl", REMOTE_SERVER_LIST_URL)
        root.put("RemoteServerListSignaturePublicKey", REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY)
        root.put("RemoteServerListDownloadFilename", REMOTE_SERVER_LIST_FILENAME)
        root.put("UseIndistinguishableTLS", true)
        root.put("LocalHttpProxyPort", HTTP_PROXY_PORT)
        root.put("LocalSocksProxyPort", SOCKS_PROXY_PORT)

        // Proxy sharing (LAN / hotspot): bind the local HTTP + SOCKS proxies
        // on all interfaces so other devices can point their proxy settings
        // at this phone. Pairs with the VPN's LAN route exclusion
        // (VpnManager.setShareProxyOnNetwork) which keeps private subnets
        // out of the tunnel so the clients can actually reach the listener.
        if (options.enableLan) {
            root.put("ListenInterface", "any")
        }

        // ----- desktop build_config parity -----
        root.put("EgressRegion", options.region.trim())
        root.put("EmitBytesTransferred", true)
        root.put("EmitDiagnosticNotices", true)
        root.put("EmitServerAlerts", true)
        root.put("EmitDiagnosticNetworkParameters", true)
        root.put(
            "DNSResolverAlternateServers",
            JsonUtil.stringArray(listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4"))
        )

        if (options.enableTimeout) {
            // The AAR default (0 = wait indefinitely) is kept.
        } else {
            // Timeout disabled: wait indefinitely and dial patiently — the
            // lambda substantially improves first-connect success on
            // high-latency censored networks (desktop comment, verbatim).
            root.put("EstablishTunnelTimeoutSeconds", 0)
            root.put("NetworkLatencyMultiplierLambda", 0.1)
        }

        if (options.enableBeastMode) {
            root.put("AggressiveEstablishment", true)
        }
        // Worker pool: how many candidates are dialed in parallel. Always
        // applied now (v2.4.0): previously it was only sent when > 30, so
        // the effective pool silently depended on the beast-mode default.
        // 64 is the core's aggressive pool; honor a user-picked higher
        // value up to 256 for maximum parallel dial pressure. (The core
        // clamps to its own limits; AggressiveEstablishment raises the
        // floor to the core's aggressive pool size.)
        val workers = maxOf(options.connectionWorkers, 64).coerceAtMost(256)
        root.put("ConnectionWorkerPoolSize", workers)

        // Turbo network optimizations — BBR congestion control, 4 MB socket
        // buffers and tuned keepalives on every dial socket (core
        // EnableTCPOptimizations). On by default: substantially better
        // sustained throughput on lossy, high-latency mobile networks.
        root.put("EnableTCPOptimizations", options.enableTurboNetwork)

        // ----------------------------------------------------------------
        // DPI-evasion layer (v2.4.1) — research-driven (Iran field data).
        //
        // The second user log showed every direct TCP dial to Psiphon
        // server IPs timing out (IP-level blackholing) — the only surviving
        // paths are TLS to CDN edges. Iran's remaining lever there is
        // SNI-based RST injection and TLS size/timing fingerprinting.
        // These knobs (upstream fields, used by Psiphon ops against the
        // same DPI) counter exactly that:
        //
        // TLSFragmentClientHello — splits the ClientHello so SNI filters
        // can't reassemble it. This is the same class of fix as v2ray's
        // "fragment" setting that currently works in Iran.
        //
        // MeekPayloadPadding — pads meek HTTP payloads so the SSH
        // handshake's size signature doesn't match a blockable profile.
        // ----------------------------------------------------------------
        root.put("TLSFragmentClientHelloProbability", 0.8)
        root.put("TLSFragmentClientHelloLimitProtocols", JsonUtil.stringArray(listOf(
            "FRONTED-MEEK-OSSH", "FRONTED-MEEK-HTTP-OSSH",
            "FRONTED-MEEK-CDN-OSSH", "FRONTED-MEEK-CDN-HTTP-OSSH",
            "UNFRONTED-MEEK-HTTPS-OSSH", "UNFRONTED-MEEK-SESSION-TICKET-OSSH",
            "TLS-OSSH"
        )))
        root.put("MeekPayloadPaddingProbability", 0.7)

        // Protocol selection mirrors the desktop client (and the Psiphon
        // Android client it was ported from).
        when (options.protocol) {
            ProtocolMode.CONDUIT -> {
                root.put("LimitTunnelProtocols",
                    filteredProtocols(CONDUIT_PROTOCOLS, options.tcpOnlyOverride))
            }
            ProtocolMode.CDN_FRONTING -> {
                root.put("LimitTunnelProtocols",
                    filteredProtocols(CDN_FRONTING_PROTOCOLS, options.tcpOnlyOverride))
                // Tactics ON (v2.4.0 field fix): tactics are the Psiphon
                // network's remote-tuned obfuscation/performance parameters —
                // the primary adaptation mechanism when blocks shift
                // mid-session. Disabling them (the old behavior) froze the
                // client's parameters while the network moved on, and the
                // user log shows exactly that failure mode. The CDN scan
                // spec and dial overrides below still take precedence for
                // CDN-fronted dials.
                putCdnFrontingConfig(root, options)
            }
            ProtocolMode.DIRECT -> {
                root.put("LimitTunnelProtocols",
                    filteredProtocols(DIRECT_PROTOCOLS, options.tcpOnlyOverride))
                putCdnFrontingConfig(root, options)
            }
            ProtocolMode.EXTREME -> {
                root.put("LimitTunnelProtocols",
                    filteredProtocols(EXTREME_PROTOCOLS, options.tcpOnlyOverride))
                putCdnFrontingConfig(root, options)
            }
            ProtocolMode.AUTO -> {
                putCdnFrontingConfig(root, options)
            }
        }

        val compartment = options.inproxyCompartmentId.trim()
        if (compartment.isNotEmpty() &&
            (options.protocol == ProtocolMode.CONDUIT || options.protocol == ProtocolMode.EXTREME)) {
            root.put("InproxyClientPersonalCompartmentID", compartment)
        }

        val upstream = options.upstreamProxyUrl.trim()
        if (upstream.isNotEmpty()) {
            root.put("UpstreamProxyURL", upstream)
        }

        val oslRoots = parseOslUrls(options.oslUrls)
        if (oslRoots.isNotEmpty()) {
            root.put("ObfuscatedServerListRootURLs", JsonUtil.stringArray(oslRoots))
        }

        return root.toString()
    }

    private fun filteredProtocols(protocols: List<String>, tcpOnly: Boolean): JSONArray {
        val list = if (tcpOnly) protocols.filter { "QUIC" !in it } else protocols
        return JsonUtil.stringArray(list)
    }

    // ----- CDN fronting (port of putCdnFrontingConfig /
    // makeCdnFrontingDialOverrides / makeCdnFrontingScanSpec) -----

    private fun putCdnFrontingConfig(root: JSONObject, options: TunnelOptions) {
        root.put("FrontedMeekDialOverrides", cdnFrontingDialOverrides(options.cdnFrontingSni))
        root.put("FrontedMeekDialOverridesProbability", 1.0)
        root.put("FrontedMeekCDNScanUseBuiltInSpec", true)

        val spec = cdnFrontingScanSpec(options.cdnFrontingIpList, options.cdnFrontingSni)
        if (spec != null) {
            root.put("FrontedMeekCDNScanSpec", spec)
        }
    }

    private fun cdnFrontingScanSpec(customIpList: String, customSni: String): JSONObject? {
        val ipCandidates = parseCdnFrontingIpCandidates(customIpList)
        if (ipCandidates.isEmpty()) return null

        val spec = JSONObject()
        spec.put("IPCandidates", JsonUtil.stringArray(ipCandidates))
        val sniNames = parseCdnFrontingSniList(customSni)
        if (sniNames.isNotEmpty()) {
            spec.put("SNIServerNames", JsonUtil.stringArray(sniNames))
        }
        return spec
    }

    /** Split on whitespace/comma/semicolon; accept IPv4 or IPv4 CIDR only; dedupe. */
    fun parseCdnFrontingIpCandidates(customIpList: String): List<String> {
        val candidates = ArrayList<String>()
        if (customIpList.isBlank()) return candidates
        for (entry in splitSettingEntries(customIpList)) {
            val candidate = entry.trim()
            if (candidate.isEmpty()) continue
            if (!isValidIpv4Address(candidate) && !isValidIpv4Cidr(candidate)) continue
            if (candidate !in candidates) candidates.add(candidate)
        }
        return candidates
    }

    /** Split, normalize each hostname, drop invalid entries, dedupe. */
    fun parseCdnFrontingSniList(customSni: String): List<String> {
        val names = ArrayList<String>()
        if (customSni.isBlank()) return names
        for (entry in splitSettingEntries(customSni)) {
            val sni = normalizeHostname(entry)
            if (sni.isEmpty()) continue
            if (sni !in names) names.add(sni)
        }
        return names
    }

    private fun splitSettingEntries(value: String): List<String> =
        value.split(' ', '\t', '\r', '\n', ',', ';').filter { it.isNotEmpty() }

    fun parseOslUrls(value: String): List<String> {
        val urls = ArrayList<String>()
        if (value.isBlank()) return urls
        for (entry in splitSettingEntries(value)) {
            val url = entry.trim()
            if (url.isEmpty()) continue
            if (!url.startsWith("https://") && !url.startsWith("http://")) continue
            if (url !in urls) urls.add(url)
        }
        return urls
    }

    private fun isValidIpv4Address(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return false
            for (c in part) if (c !in '0'..'9') return false
            val value = part.toIntOrNull() ?: return false
            if (value !in 0..255) return false
        }
        return true
    }

    private fun isValidIpv4Cidr(cidr: String): Boolean {
        if (cidr.isBlank()) return false
        val parts = cidr.split("/")
        if (parts.size != 2 || !isValidIpv4Address(parts[0])) return false
        for (c in parts[1]) if (c !in '0'..'9') return false
        val prefix = parts[1].toIntOrNull() ?: return false
        return prefix in 0..32
    }

    private fun isValidHostname(hostname: String): Boolean {
        if (hostname.isEmpty() || hostname.length > 253 || isValidIpv4Address(hostname)) {
            return false
        }
        var normalized = hostname
        if (normalized.endsWith(".")) normalized = normalized.dropLast(1)
        if (normalized.isEmpty()) return false
        for (label in normalized.split(".")) {
            if (label.isEmpty() || label.length > 63 ||
                label.startsWith("-") || label.endsWith("-")) {
                return false
            }
            for (c in label) {
                val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-'
                if (!ok) return false
            }
        }
        return true
    }

    fun normalizeHostname(hostname: String): String {
        if (hostname.isBlank()) return ""
        var normalized = hostname.trim().lowercase()
        if (normalized.endsWith(".")) normalized = normalized.dropLast(1)
        if (!isValidHostname(normalized)) return ""
        return normalized
    }

    private fun cdnFrontingDialOverrides(customSni: String): JSONArray {
        val overrides = JSONArray()

        // First valid custom SNI entry or "" (normalizeCdnFrontingCustomSni).
        val sniNames = parseCdnFrontingSniList(customSni)
        val edgeSni = sniNames.firstOrNull() ?: ""

        val fastlyVerify = listOf(
            "www.python.org", "pypi.org", "fastly.com", "www.fastly.com",
            "developer.fastly.com", "githubassets.com", "github.com",
            "github.io", "githubusercontent.com"
        )
        val fastlyAlpn = listOf("h2", "http/1.1")

        overrides.put(cdnFrontingOverride(
            "fastly-provider",
            matchProvider = listOf("(?i)fastly"),
            matchDial = null,
            dialAddress = "pypi.org",
            sni = "pypi.org",
            verifyNames = fastlyVerify,
            alpn = fastlyAlpn
        ))
        overrides.put(cdnFrontingOverride(
            "fastly-address",
            matchProvider = null,
            matchDial = listOf("(?i)(fastly|pypi|python|github)"),
            dialAddress = "pypi.org",
            sni = "pypi.org",
            verifyNames = fastlyVerify,
            alpn = fastlyAlpn
        ))

        // Edge candidate pool — identical to the desktop client.
        val edgeIds = listOf(
            "edge-a-1", "edge-a-2", "edge-b-1", "edge-b-2",
            "edge-c-1", "edge-c-2", "edge-d-1", "edge-d-2", "edge-original"
        )
        val edgeIps = listOf(
            "23.215.0.206", "23.215.0.203",
            "23.212.250.91", "23.212.250.78",
            "23.12.147.13", "23.12.147.29",
            "23.73.207.8", "23.73.207.15",
            "92.123.102.43"
        )
        for (i in edgeIds.indices) {
            val ip = edgeIps[i]
            val edgeOverrideSni = if (edgeSni.isNotEmpty()) edgeSni else ip
            overrides.put(cdnFrontingOverride(
                edgeIds[i],
                matchProvider = null,
                matchDial = listOf(".*"),
                dialAddress = ip,
                sni = edgeOverrideSni,
                verifyNames = edgeVerifyServerNames(ip, edgeOverrideSni),
                alpn = listOf("http/1.1")
            ))
        }

        return overrides
    }

    private fun cdnFrontingOverride(
        overrideId: String,
        matchProvider: List<String>?,
        matchDial: List<String>?,
        dialAddress: String,
        sni: String,
        verifyNames: List<String>,
        alpn: List<String>
    ): JSONObject {
        val obj = JSONObject()
        obj.put("OverrideID", overrideId)
        if (matchProvider != null) {
            obj.put("MatchFrontingProviderIDRegexes", JsonUtil.stringArray(matchProvider))
        }
        if (matchDial != null) {
            obj.put("MatchDialAddressRegexes", JsonUtil.stringArray(matchDial))
        }
        obj.put("DialAddresses", JsonUtil.stringArray(listOf(dialAddress)))
        obj.put("SNIServerName", sni)
        obj.put("VerifyServerNames", JsonUtil.stringArray(verifyNames))
        obj.put("ALPNProtocols", JsonUtil.stringArray(alpn))
        obj.put("TLSProfile", "Chrome-83")
        return obj
    }

    private fun edgeVerifyServerNames(ip: String, sni: String): List<String> {
        val candidates = listOf(
            sni, ip, "a248.e.akamai.net", "a.akamaized.net",
            "a.akamaized-staging.net", "a.akamaihd.net",
            "a.akamaihd-staging.net", "www.akamai.com"
        )
        val names = ArrayList<String>()
        for (candidate in candidates) {
            if (candidate !in names) names.add(candidate)
        }
        return names
    }
}
