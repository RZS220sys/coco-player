package com.player.coco.xray

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class ParsedXrayOutbound(
    val outbound: JSONObject,
    val remark: String,
)

object XrayShareLinkParser {
    private val uriRegex = Regex("""(?i)\b(?:vless|vmess|trojan|ss)://[^\s'"<>]+""")

    fun extractLinks(subscriptionText: String): List<String> {
        val variants = mutableListOf(subscriptionText)
        maybeDecodeBase64Subscription(subscriptionText)?.let { variants.add(it) }

        val seen = linkedSetOf<String>()
        variants.forEach { variant ->
            uriRegex.findAll(variant).forEach { match ->
                match.value.trim().trimEnd(',', ';').takeIf { it.isNotBlank() }?.let { seen.add(it) }
            }

            variant.split(Regex("""[\r\n\t ]+""")).forEach { rawToken ->
                val token = rawToken.trim().trim('\'', '"').trimEnd(',', ';')
                if (token.startsWith("vless://", true) ||
                    token.startsWith("vmess://", true) ||
                    token.startsWith("trojan://", true) ||
                    token.startsWith("ss://", true)
                ) {
                    seen.add(token)
                }
            }
        }

        return seen.toList()
    }

    fun canonicalWithoutFragment(uri: String): String {
        return uri.substringBefore("#").trim()
    }

    fun parse(uri: String): ParsedXrayOutbound {
        return when (uri.substringBefore("://").lowercase()) {
            "vless" -> parseVless(uri)
            "vmess" -> parseVmess(uri)
            "trojan" -> parseTrojan(uri)
            "ss" -> parseShadowsocks(uri)
            else -> error("Unsupported URI scheme: ${uri.substringBefore("://")}")
        }
    }

    private fun parseVless(uri: String): ParsedXrayOutbound {
        val parsed = URI(uri)
        val query = parseQuery(parsed.rawQuery)
        val id = decodeComponent(parsed.rawUserInfo.orEmpty())
        val host = parsed.host.orEmpty()
        if (id.isBlank()) error("VLESS URI has no user/id")
        if (host.isBlank()) error("VLESS URI has no host")

        val security = first(query, "security", default = "none").lowercase()
        val port = parsed.port.takeIf { it > 0 } ?: if (security == "tls" || security == "reality") 443 else 80
        val user = JSONObject()
            .put("id", id)
            .put("encryption", first(query, "encryption", default = "none").ifBlank { "none" })
            .put("level", first(query, "level", default = "8").toIntOrNull() ?: 8)

        putIfNotBlank(user, "flow", first(query, "flow", default = ""))

        val outbound = JSONObject()
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", buildTransport(query))

        return ParsedXrayOutbound(outbound, decodeComponent(parsed.rawFragment.orEmpty()))
    }

    private fun parseTrojan(uri: String): ParsedXrayOutbound {
        val parsed = URI(uri)
        val query = parseQuery(parsed.rawQuery)
        val password = decodeComponent(parsed.rawUserInfo.orEmpty())
        val host = parsed.host.orEmpty()
        if (password.isBlank()) error("Trojan URI has no password")
        if (host.isBlank()) error("Trojan URI has no host")

        if (!query.containsKey("security")) {
            query["security"] = listOf("tls")
        }

        val server = JSONObject()
            .put("address", host)
            .put("port", parsed.port.takeIf { it > 0 } ?: 443)
            .put("password", password)
            .put("level", 8)
        putIfNotBlank(server, "flow", first(query, "flow", default = ""))

        val outbound = JSONObject()
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", buildTransport(query))

        return ParsedXrayOutbound(outbound, decodeComponent(parsed.rawFragment.orEmpty()))
    }

    private fun parseVmess(uri: String): ParsedXrayOutbound {
        val body = uri.removePrefix("vmess://").substringBefore("#")
        val data = JSONObject(decodeBase64Text(body))
        val host = data.optString("add").ifBlank { data.optString("address") }
        val id = data.optString("id")
        if (host.isBlank()) error("VMess URI has no host/add")
        if (id.isBlank()) error("VMess URI has no id")

        val port = data.optString("port").toIntOrNull() ?: if (data.optString("tls").isNotBlank()) 443 else 80
        val user = JSONObject()
            .put("id", id)
            .put("alterId", data.optString("aid").toIntOrNull() ?: 0)
            .put("security", data.optString("scy").ifBlank { data.optString("security").ifBlank { "auto" } })
            .put("level", 8)

        val params = mutableMapOf(
            "type" to listOf(data.optString("net").ifBlank { data.optString("type").ifBlank { "tcp" } }),
            "security" to listOf(data.optString("tls").ifBlank { "none" }),
            "sni" to listOf(data.optString("sni").ifBlank { data.optString("peer") }),
            "alpn" to listOf(data.optString("alpn")),
            "fp" to listOf(data.optString("fp")),
            "host" to listOf(data.optString("host")),
            "path" to listOf(data.optString("path")),
        )

        val outbound = JSONObject()
            .put("protocol", "vmess")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", buildTransport(params))

        return ParsedXrayOutbound(outbound, data.optString("ps"))
    }

    private fun parseShadowsocks(uri: String): ParsedXrayOutbound {
        val parsed = URI(uri)
        val remark = decodeComponent(parsed.rawFragment.orEmpty())
        var method = ""
        var password = ""
        var host = parsed.host.orEmpty()
        var port = parsed.port.takeIf { it > 0 } ?: 8388

        if (host.isNotBlank()) {
            val userInfo = decodeComponent(parsed.rawUserInfo.orEmpty())
            if (":" in userInfo) {
                val parts = userInfo.split(":", limit = 2)
                method = parts[0]
                password = parts[1]
            } else {
                val decoded = decodeBase64Text(userInfo)
                val parts = decoded.split(":", limit = 2)
                if (parts.size != 2) error("Bad Shadowsocks userinfo")
                method = parts[0]
                password = parts[1]
            }
        } else {
            val body = uri.removePrefix("ss://").substringBefore("#").substringBefore("?")
            val decoded = decodeBase64Text(body)
            val parts = decoded.split("@", limit = 2)
            if (parts.size != 2) error("Bad Shadowsocks legacy URI")
            val userParts = parts[0].split(":", limit = 2)
            if (userParts.size != 2) error("Bad Shadowsocks legacy userinfo")
            method = userParts[0]
            password = userParts[1]

            val server = parts[1]
            if (server.startsWith("[")) {
                host = server.substringAfter("[").substringBefore("]")
                port = server.substringAfter("]").trimStart(':').toIntOrNull() ?: 8388
            } else {
                host = server.substringBeforeLast(":")
                port = server.substringAfterLast(":").toIntOrNull() ?: 8388
            }
        }

        if (method.isBlank() || password.isBlank() || host.isBlank()) {
            error("Incomplete Shadowsocks URI")
        }

        val outbound = JSONObject()
            .put("protocol", "shadowsocks")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("method", method)
                            .put("password", password)
                            .put("level", 8)
                            .put("uot", true)
                    )
                )
            )

        return ParsedXrayOutbound(outbound, remark)
    }

    private fun buildTransport(params: Map<String, List<String>>): JSONObject {
        val network = normalizeNetwork(first(params, "type", "net", "network", default = "tcp"))
        val security = first(params, "security", "tls", default = "none")
            .lowercase()
            .let { if (it.isBlank() || it == "0" || it == "false") "none" else it }

        val stream = JSONObject()
            .put("network", network)
            .put("security", security)

        val sni = first(params, "sni", "serverName", "servername", "peer", default = "")
        val alpn = splitCsv(first(params, "alpn", default = ""))
        val fingerprint = first(params, "fp", "fingerprint", default = "")
        val allowInsecure = truthy(first(params, "allowInsecure", "allowinsecure", "insecure", default = ""))

        if (security == "tls") {
            val tls = JSONObject()
            putIfNotBlank(tls, "serverName", sni)
            if (allowInsecure) tls.put("allowInsecure", true)
            if (alpn.isNotEmpty()) tls.put("alpn", JSONArray(alpn))
            putIfNotBlank(tls, "fingerprint", fingerprint)
            stream.put("tlsSettings", tls)
        } else if (security == "reality") {
            val reality = JSONObject()
            putIfNotBlank(reality, "serverName", sni)
            putIfNotBlank(reality, "fingerprint", fingerprint)
            putIfNotBlank(reality, "publicKey", first(params, "pbk", "publicKey", "publickey", default = ""))
            putIfNotBlank(reality, "shortId", first(params, "sid", "shortId", "shortid", default = ""))
            putIfNotBlank(reality, "spiderX", first(params, "spx", "spiderX", "spiderx", default = ""))
            stream.put("realitySettings", reality)
        }

        val host = first(params, "host", "authority", default = "")
        val path = first(params, "path", default = "")
        val mode = first(params, "mode", default = "")

        when (network) {
            "xhttp" -> {
                val xhttp = JSONObject()
                putIfNotBlank(xhttp, "host", host)
                putIfNotBlank(xhttp, "path", path)
                putIfNotBlank(xhttp, "mode", mode)
                parseJsonIfPossible(first(params, "extra", default = ""))?.let { xhttp.put("extra", it) }
                stream.put("xhttpSettings", xhttp)
            }

            "ws" -> {
                val ws = JSONObject()
                putIfNotBlank(ws, "path", path)
                if (host.isNotBlank()) ws.put("headers", JSONObject().put("Host", host))
                stream.put("wsSettings", ws)
            }

            "grpc" -> {
                val grpc = JSONObject()
                putIfNotBlank(grpc, "serviceName", first(params, "serviceName", "path", default = "").trimStart('/'))
                putIfNotBlank(grpc, "authority", host)
                if (mode.lowercase() in setOf("multi", "gun")) grpc.put("multiMode", true)
                stream.put("grpcSettings", grpc)
            }

            "httpupgrade" -> {
                val httpUpgrade = JSONObject()
                putIfNotBlank(httpUpgrade, "path", path)
                putIfNotBlank(httpUpgrade, "host", host)
                stream.put("httpupgradeSettings", httpUpgrade)
            }

            "kcp" -> {
                val kcp = JSONObject()
                putIfNotBlank(kcp, "seed", first(params, "seed", default = ""))
                putIfNotBlank(kcp, "header", first(params, "headerType", "header", default = "")) { value ->
                    JSONObject().put("type", value)
                }
                listOf("mtu", "tti", "uplinkCapacity", "downlinkCapacity", "readBufferSize", "writeBufferSize").forEach { key ->
                    first(params, key, default = "").toIntOrNull()?.let { kcp.put(key, it) }
                }
                val congestion = first(params, "congestion", default = "")
                if (congestion.isNotBlank()) kcp.put("congestion", truthy(congestion))
                stream.put("kcpSettings", kcp)
            }

            "tcp" -> {
                val headerType = first(params, "headerType", "header", default = "")
                if (headerType.isNotBlank() && !headerType.equals("none", true)) {
                    val tcp = JSONObject().put("header", JSONObject().put("type", headerType))
                    if (headerType.equals("http", true)) {
                        val request = JSONObject()
                        val paths = parseHostList(path)
                        if (paths.isNotEmpty()) request.put("path", JSONArray(paths))
                        val hosts = parseHostList(host)
                        if (hosts.isNotEmpty()) request.put("headers", JSONObject().put("Host", JSONArray(hosts)))
                        if (request.length() > 0) tcp.getJSONObject("header").put("request", request)
                    }
                    stream.put("tcpSettings", tcp)
                }
            }
        }

        return stream
    }

    private fun maybeDecodeBase64Subscription(text: String): String? {
        val compact = text.trim().replace(Regex("""\s+"""), "")
        if (compact.isBlank() || compact.take(2000).contains("://")) return null
        if (!Regex("""[A-Za-z0-9_\-+/=]+""").matches(compact)) return null
        return runCatching { decodeBase64Text(compact) }.getOrNull()?.takeIf { it.contains("://") }
    }

    private fun parseQuery(rawQuery: String?): MutableMap<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        rawQuery.orEmpty().split("&").filter { it.isNotEmpty() }.forEach { pair ->
            val key = decodeComponent(pair.substringBefore("="))
            val value = if ("=" in pair) decodeComponent(pair.substringAfter("=")) else ""
            result.getOrPut(key) { mutableListOf() }.add(value)
        }
        return result.mapValues { it.value.toList() }.toMutableMap()
    }

    private fun first(params: Map<String, List<String>>, vararg names: String, default: String): String {
        names.forEach { name ->
            params[name]?.firstOrNull()?.let { return it }
        }
        return default
    }

    private fun normalizeNetwork(raw: String): String {
        return when (raw.lowercase()) {
            "", "raw", "tcp", "http" -> "tcp"
            "websocket" -> "ws"
            "splithttp", "h2" -> "xhttp"
            "mkcp" -> "kcp"
            else -> raw.lowercase()
        }
    }

    private fun splitCsv(value: String): List<String> {
        return value.split(Regex("[,|]")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseHostList(value: String): List<String> {
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseJsonIfPossible(value: String): Any? {
        if (value.isBlank()) return null
        return runCatching { JSONObject(value) }.getOrElse {
            runCatching { JSONArray(value) }.getOrNull()
        }
    }

    private fun putIfNotBlank(target: JSONObject, key: String, value: String) {
        if (value.isNotBlank()) target.put(key, value)
    }

    private fun putIfNotBlank(target: JSONObject, key: String, value: String, transform: (String) -> Any) {
        if (value.isNotBlank()) target.put(key, transform(value))
    }

    private fun truthy(value: String): Boolean {
        return value.lowercase() in setOf("1", "true", "yes", "y", "on")
    }

    private fun decodeComponent(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun decodeBase64Text(value: String): String {
        val compact = value.trim().replace(Regex("""\s+"""), "")
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val bytes = runCatching {
            Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        }.getOrElse {
            Base64.decode(padded, Base64.DEFAULT)
        }

        return listOf(Charsets.UTF_8, Charsets.ISO_8859_1).firstNotNullOfOrNull { charset ->
            runCatching { bytes.toString(charset) }.getOrNull()
        } ?: bytes.toString(Charsets.UTF_8)
    }
}
