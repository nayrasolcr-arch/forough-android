package io.github.nayrasolcr.forough.engine

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Xray server model + share-link parsers (v2.5.0 second engine).
 *
 * Parses the standard share formats used across the v2ray/Xray ecosystem —
 * the same links v2rayNG / v2rayN / Hiddify users exchange:
 *
 *   vless://uuid@host:port?security=reality&sni=..&fp=chrome&pbk=..&sid=..&type=tcp&flow=xtls-rprx-vision#name
 *   trojan://password@host:port?security=tls&type=tcp&sni=..#name
 *   ss://base64(method:password)@host:port#name   (+ legacy fully-encoded form)
 *   vmess://base64({v,ps,add,port,id,aid,net,host,path,tls,sni,fp})
 *
 * Also fetches subscription URLs (base64-encoded or plain lists of links) —
 * this is how public/community server lists are distributed.
 */
data class XrayServer(
    val kind: String,          // vless | vmess | trojan | shadowsocks
    val name: String,
    val address: String,
    val port: Int,
    val id: String,            // uuid / password
    val flow: String = "",     // xtls-rprx-vision
    val network: String = "tcp",   // tcp | ws | grpc | httpupgrade
    val security: String = "none", // none | tls | reality
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",    // reality pbk
    val shortId: String = "",      // reality sid
    val wsPath: String = "",
    val wsHost: String = "",
    val alterId: Int = 0,          // vmess
    val ssMethod: String = "",     // shadowsocks
    val rawLink: String = ""
) {
    val protocolLabel: String
        get() = when (kind) {
            "shadowsocks" -> "SS"
            else -> kind.uppercase()
        } + when {
            security == "reality" -> "+REALITY"
            security == "tls" && network == "ws" -> "+WS"
            security == "tls" -> "+TLS"
            else -> ""
        }
}

object XrayShareLink {

    /** Parse one share link; null when the scheme is unknown or malformed. */
    fun parse(link: String): XrayServer? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
            trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            else -> null
        }
    }

    /** Parse a full subscription/clipboard blob: plain list or base64 body. */
    fun parseMany(text: String): List<XrayServer> {
        val servers = ArrayList<XrayServer>()
        val seen = HashSet<String>()
        for (line in text.split('\n', '\r')) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val parsed = parse(t) ?: continue
            if (seen.add("${parsed.kind}:${parsed.address}:${parsed.port}:${parsed.id}")) {
                servers.add(parsed)
            }
        }
        if (servers.isNotEmpty()) return servers

        // Base64-encoded subscription body → decode and retry.
        val decoded = try {
            String(Base64.decode(text.trim(), Base64.DEFAULT))
        } catch (_: Exception) {
            return emptyList()
        }
        for (line in decoded.split('\n', '\r')) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val parsed = parse(t) ?: continue
            if (seen.add("${parsed.kind}:${parsed.address}:${parsed.port}:${parsed.id}")) {
                servers.add(parsed)
            }
        }
        return servers
    }

    /** Fetch a subscription URL and parse the body (network call — background thread). */
    fun fetchSubscription(url: String, timeoutMs: Int = 10_000): List<XrayServer> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "v2rayNG/1.9")
        return try {
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            parseMany(body)
        } finally {
            conn.disconnect()
        }
    }

    // ----- individual schemes -----

    private fun parseVless(link: String): XrayServer? {
        return try {
            val body = link.removePrefix("vless://")
            val fragment = body.substringAfter('#', "")
            val name = URLDecoder.decode(fragment, "UTF-8").ifBlank { "VLESS" }
            val main = body.substringBefore('#')
            val query = main.substringAfter('?', "")
            val userinfo = main.substringBefore('?')
            val id = URLDecoder.decode(userinfo.substringBefore('@'), "UTF-8")
            val hostPort = userinfo.substringAfter('@', "")
            val host = hostPort.substringBeforeLast(':')
            val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
            val q = parseQuery(query)
            XrayServer(
                kind = "vless", name = name, address = host, port = port, id = id,
                flow = q["flow"] ?: "",
                network = (q["type"] ?: "tcp").let {
                    if (it == "raw") "tcp" else it
                },
                security = q["security"] ?: if ((q["tls"] ?: "") == "1") "tls" else "none",
                sni = q["sni"] ?: q["host"] ?: "",
                fingerprint = q["fp"] ?: "chrome",
                publicKey = q["pbk"] ?: "",
                shortId = q["sid"] ?: "",
                wsPath = q["path"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "",
                wsHost = q["host"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "",
                rawLink = link
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTrojan(link: String): XrayServer? {
        return try {
            val body = link.removePrefix("trojan://")
            val fragment = body.substringAfter('#', "")
            val name = URLDecoder.decode(fragment, "UTF-8").ifBlank { "Trojan" }
            val main = body.substringBefore('#')
            val query = main.substringAfter('?', "")
            val userinfo = main.substringBefore('?')
            val password = URLDecoder.decode(userinfo.substringBefore('@'), "UTF-8")
            val hostPort = userinfo.substringAfter('@', "")
            val host = hostPort.substringBeforeLast(':')
            val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
            val q = parseQuery(query)
            XrayServer(
                kind = "trojan", name = name, address = host, port = port, id = password,
                network = (q["type"] ?: "tcp").let { if (it == "raw") "tcp" else it },
                security = q["security"] ?: "tls",
                sni = q["sni"] ?: q["peer"] ?: "",
                fingerprint = q["fp"] ?: "chrome",
                wsPath = q["path"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "",
                wsHost = q["host"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "",
                rawLink = link
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(link: String): XrayServer? {
        return try {
            val body = link.removePrefix("ss://")
            val fragment = body.substringAfter('#', "")
            val name = URLDecoder.decode(fragment, "UTF-8").ifBlank { "Shadowsocks" }
            val main = body.substringBefore('#').substringBefore('?')
            // Form 1: base64(method:password)@host:port
            // Form 2: base64(method:password@host:port)
            val at = main.lastIndexOf('@')
            if (at > 0) {
                val userInfo = try {
                    String(Base64.decode(main.substring(0, at), Base64.DEFAULT))
                } catch (_: Exception) {
                    main.substring(0, at)
                }
                val method = userInfo.substringBefore(':')
                val password = userInfo.substringAfter(':', "")
                val hostPort = main.substring(at + 1)
                val host = hostPort.substringBeforeLast(':')
                val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
                XrayServer(
                    kind = "shadowsocks", name = name, address = host, port = port,
                    id = password, ssMethod = method, rawLink = link
                )
            } else {
                val decoded = String(Base64.decode(main, Base64.DEFAULT))
                val method = decoded.substringBefore(':')
                val rest = decoded.substringAfter(':', "")
                val password = rest.substringBefore('@')
                val hostPort = rest.substringAfter('@')
                val host = hostPort.substringBeforeLast(':')
                val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
                XrayServer(
                    kind = "shadowsocks", name = name, address = host, port = port,
                    id = password, ssMethod = method, rawLink = link
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVmess(link: String): XrayServer? {
        return try {
            val json = String(Base64.decode(link.removePrefix("vmess://"), Base64.DEFAULT))
            val o = org.json.JSONObject(json)
            val name = o.optString("ps", "VMess")
            val host = o.optString("add", "")
            val port = o.optString("port").toIntOrNull() ?: return null
            if (host.isEmpty()) return null
            XrayServer(
                kind = "vmess", name = name, address = host, port = port,
                id = o.optString("id", ""),
                alterId = o.optString("aid", "0").toIntOrNull() ?: 0,
                network = o.optString("net", "tcp").let { if (it == "raw") "tcp" else it },
                security = o.optString("tls", "").ifBlank { "none" },
                sni = o.optString("sni", o.optString("host", "")),
                fingerprint = o.optString("fp", "chrome"),
                wsPath = o.optString("path", ""),
                wsHost = o.optString("host", ""),
                rawLink = link
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val k = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            val v = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            map[k] = v
        }
        return map
    }
}
