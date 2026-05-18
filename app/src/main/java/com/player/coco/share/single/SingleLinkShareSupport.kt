package com.player.coco.share.single

import android.net.Uri
import android.util.Base64
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkEndpoint
import org.json.JSONObject

internal fun draft(name: String, protocol: String, values: JSONObject): SingleLinkDraft {
    return SingleLinkDraft(
        name = name.ifBlank { SingleLinkEndpoint.fromValues(values) },
        protocol = protocol,
        values = values,
        endpoint = SingleLinkEndpoint.fromValues(values),
        settings = JSONObject(),
    )
}

internal fun serverValues(parsed: Uri, defaultPort: Int): JSONObject? {
    val host = parsed.host.orEmpty()
    if (host.isBlank()) {
        return null
    }
    return JSONObject()
        .put(SingleLinkValueKeys.ADDRESS, host)
        .put(SingleLinkValueKeys.PORT, (parsed.port.takeIf { it > 0 } ?: defaultPort).toString())
}

internal fun serverValues(raw: String, defaultPort: Int): JSONObject? {
    val host: String
    val port: Int
    if (raw.startsWith("[")) {
        host = raw.substringAfter("[").substringBefore("]")
        port = raw.substringAfter("]").trimStart(':').toIntOrNull() ?: defaultPort
    } else {
        host = raw.substringBeforeLast(":", missingDelimiterValue = raw)
        port = raw.substringAfterLast(":", missingDelimiterValue = defaultPort.toString()).toIntOrNull()
            ?: defaultPort
    }
    if (host.isBlank()) {
        return null
    }
    return JSONObject()
        .put(SingleLinkValueKeys.ADDRESS, host)
        .put(SingleLinkValueKeys.PORT, port.toString())
}

internal fun defaultPort(query: Map<String, String>, portForSecurity: (String) -> Int): Int {
    return portForSecurity(normalizeSecurity(first(query, "security", "tls")))
}

internal fun linkName(parsed: Uri, values: JSONObject): String {
    return decodeComponent(parsed.encodedFragment.orEmpty()).ifBlank { SingleLinkEndpoint.fromValues(values) }
}

internal fun linkBody(link: String): String {
    return link.trim().substringAfter("://", missingDelimiterValue = "")
}

internal fun parseQuery(rawQuery: String?): Map<String, String> {
    return rawQuery.orEmpty()
        .split("&")
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val key = decodeComponent(pair.substringBefore("="))
            val value = if ("=" in pair) decodeComponent(pair.substringAfter("=")) else ""
            key to value
        }
}

internal fun first(params: Map<String, String>, vararg names: String): String {
    names.forEach { name ->
        params[name]?.let { return it }
    }
    return ""
}

internal fun normalizeSecurity(value: String): String {
    return value.lowercase().let {
        if (it.isBlank() || it == "0" || it == "false") "none" else it
    }
}

internal fun normalizeNetwork(value: String): String {
    return when (value.lowercase()) {
        "", "raw" -> "tcp"
        "websocket" -> "ws"
        "splithttp", "h2" -> "xhttp"
        else -> value.lowercase()
    }
}

internal fun putIfNotBlank(target: JSONObject, key: String, value: String) {
    if (value.isNotBlank()) {
        target.put(key, value)
    }
}

internal fun putParamIfNotBlank(target: MutableMap<String, String>, key: String, value: String) {
    if (value.isNotBlank()) {
        target[key] = value
    }
}

internal fun putIfNotEmpty(target: JSONObject, key: String, value: JSONObject) {
    if (value.length() > 0) {
        target.put(key, value)
    }
}

internal fun truthy(value: String): Boolean {
    return value.lowercase() in setOf("1", "true", "yes", "y", "on")
}

internal fun hostForUri(host: String): String {
    return if (":" in host && !host.startsWith("[") && !host.endsWith("]")) "[$host]" else host
}

internal fun encodeComponent(value: String): String {
    return Uri.encode(value).orEmpty()
}

internal fun decodeComponent(value: String): String {
    return Uri.decode(value).orEmpty()
}

internal fun encodeBase64(value: String, removePadding: Boolean): String {
    val encoded = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return if (removePadding) encoded.trimEnd('=') else encoded
}

internal fun decodeBase64Text(value: String): String {
    val compact = value.trim().replace(Regex("""\s+"""), "")
    val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
    val bytes = runCatching {
        Base64.decode(padded, Base64.NO_WRAP)
    }.getOrElse {
        Base64.decode(padded, Base64.NO_WRAP or Base64.URL_SAFE)
    }
    return bytes.toString(Charsets.UTF_8)
}

internal fun encodeAuthorityLink(
    scheme: String,
    userInfo: String,
    name: String,
    values: JSONObject,
    params: Map<String, String>,
): String? {
    val server = values.optJSONObject(SingleLinkValueKeys.SECTION_SERVER) ?: return null
    val address = server.optString(SingleLinkValueKeys.ADDRESS)
    val port = server.optString(SingleLinkValueKeys.PORT).toIntOrNull() ?: return null
    if (address.isBlank() || port <= 0) {
        return null
    }

    val authority = if (userInfo.isBlank()) {
        "${hostForUri(address)}:$port"
    } else {
        "${encodeComponent(userInfo)}@${hostForUri(address)}:$port"
    }
    val query = params
        .filterValues { it.isNotBlank() }
        .entries
        .joinToString("&") { (key, value) -> "${encodeComponent(key)}=${encodeComponent(value)}" }
        .takeIf { it.isNotBlank() }
        ?.let { "?$it" }
        .orEmpty()
    val fragment = name.takeIf { it.isNotBlank() }?.let { "#${encodeComponent(it)}" }.orEmpty()
    return "$scheme://$authority$query$fragment"
}

internal fun decodeUserPassword(encodedUserInfo: String): Pair<String, String>? {
    val decodedUser = decodeComponent(encodedUserInfo)
    if (decodedUser.isBlank()) {
        return null
    }
    val userInfo = if (":" in decodedUser) decodedUser else decodeBase64Text(decodedUser)
    return splitUserPassword(userInfo)
}

internal fun decodeRawUserPassword(encodedUserInfo: String): Pair<String, String>? {
    val decoded = decodeComponent(encodedUserInfo)
    if (decoded.isBlank()) {
        return null
    }
    return splitUserPassword(decoded)
}

private fun splitUserPassword(decoded: String): Pair<String, String>? {
    val parts = decoded.split(":", limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else null
}
