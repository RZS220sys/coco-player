package com.player.coco.xray.single

import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONArray
import org.json.JSONObject

object SingleLinkOutboundCommon {
    fun vnextSettings(values: JSONObject, user: JSONObject): JSONObject {
        return JSONObject().put(
            "vnext",
            JSONArray().put(
                baseServer(values)
                    .put("users", JSONArray().put(user))
            )
        )
    }

    fun baseServer(values: JSONObject): JSONObject {
        val server = requiredSection(values, SingleLinkValueKeys.SECTION_SERVER, "Server")
        return JSONObject()
            .put("address", required(server, SingleLinkValueKeys.ADDRESS, "Address"))
            .put("port", server.optString(SingleLinkValueKeys.PORT).toIntOrNull() ?: error("Port is required."))
    }

    fun buildTransport(values: JSONObject, defaultSecurity: String = "none"): JSONObject {
        val transport = values.optJSONObject(SingleLinkValueKeys.SECTION_TRANSPORT)
        val network = transport?.optString(SingleLinkValueKeys.NETWORK)?.ifBlank { "tcp" } ?: "tcp"
        val security = transport?.optString(SingleLinkValueKeys.STREAM_SECURITY)?.ifBlank { defaultSecurity } ?: defaultSecurity
        val stream = JSONObject()
            .put("network", network)
            .put("security", security)

        when (security) {
            "tls" -> {
                values.optJSONObject(SingleLinkValueKeys.SECTION_TLS)?.let { tlsValues ->
                    val tls = JSONObject()
                    putIfNotBlank(tls, "serverName", tlsValues.optString(SingleLinkValueKeys.SNI))
                    splitList(tlsValues.optString(SingleLinkValueKeys.ALPN)).takeIf { it.isNotEmpty() }?.let {
                        tls.put("alpn", JSONArray(it))
                    }
                    putIfNotBlank(tls, "fingerprint", tlsValues.optString(SingleLinkValueKeys.FINGERPRINT))
                    if (tlsValues.optBoolean(SingleLinkValueKeys.ALLOW_INSECURE, false)) {
                        tls.put("allowInsecure", true)
                    }
                    putIfNotEmpty(stream, "tlsSettings", tls)
                }
            }
            "reality" -> {
                val realityValues = requiredSection(values, SingleLinkValueKeys.SECTION_REALITY, "Reality settings")
                val reality = JSONObject()
                putIfNotBlank(reality, "serverName", realityValues.optString(SingleLinkValueKeys.SNI))
                putIfNotBlank(reality, "fingerprint", realityValues.optString(SingleLinkValueKeys.FINGERPRINT))
                reality.put("publicKey", required(realityValues, SingleLinkValueKeys.PUBLIC_KEY, "Reality public key"))
                putIfNotBlank(reality, "shortId", realityValues.optString(SingleLinkValueKeys.SHORT_ID))
                putIfNotBlank(reality, "spiderX", realityValues.optString(SingleLinkValueKeys.SPIDER_X))
                putIfNotEmpty(stream, "realitySettings", reality)
            }
        }

        when (network) {
            "ws" -> {
                values.optJSONObject(SingleLinkValueKeys.SECTION_WEBSOCKET)?.let { wsValues ->
                    val ws = JSONObject()
                    putIfNotBlank(ws, "path", wsValues.optString(SingleLinkValueKeys.PATH))
                    wsValues.optString(SingleLinkValueKeys.HOST).takeIf { it.isNotBlank() }?.let {
                        ws.put("headers", JSONObject().put("Host", it))
                    }
                    putIfNotEmpty(stream, "wsSettings", ws)
                }
            }
            "grpc" -> {
                values.optJSONObject(SingleLinkValueKeys.SECTION_GRPC)?.let { grpcValues ->
                    val grpc = JSONObject()
                    putIfNotBlank(grpc, "serviceName", grpcValues.optString(SingleLinkValueKeys.PATH).trimStart('/'))
                    putIfNotBlank(grpc, "authority", grpcValues.optString(SingleLinkValueKeys.HOST))
                    putIfNotEmpty(stream, "grpcSettings", grpc)
                }
            }
            "xhttp" -> {
                values.optJSONObject(SingleLinkValueKeys.SECTION_XHTTP)?.let { xhttpValues ->
                    val xhttp = JSONObject()
                    putIfNotBlank(xhttp, "host", xhttpValues.optString(SingleLinkValueKeys.HOST))
                    putIfNotBlank(xhttp, "path", xhttpValues.optString(SingleLinkValueKeys.PATH))
                    putIfNotBlank(xhttp, "mode", xhttpValues.optString(SingleLinkValueKeys.MODE))
                    putIfNotEmpty(stream, "xhttpSettings", xhttp)
                }
            }
            "httpupgrade" -> {
                values.optJSONObject(SingleLinkValueKeys.SECTION_HTTP_UPGRADE)?.let { httpUpgradeValues ->
                    val httpUpgrade = JSONObject()
                    putIfNotBlank(httpUpgrade, "host", httpUpgradeValues.optString(SingleLinkValueKeys.HOST))
                    putIfNotBlank(httpUpgrade, "path", httpUpgradeValues.optString(SingleLinkValueKeys.PATH))
                    putIfNotEmpty(stream, "httpupgradeSettings", httpUpgrade)
                }
            }
        }

        return stream
    }

    fun required(values: JSONObject, key: String, label: String): String {
        return values.optString(key).ifBlank { error("$label is required.") }
    }

    fun requiredSection(values: JSONObject, key: String, label: String): JSONObject {
        return values.optJSONObject(key) ?: error("$label section is required.")
    }

    fun putIfNotBlank(target: JSONObject, key: String, value: String) {
        if (value.isNotBlank()) {
            target.put(key, value)
        }
    }

    private fun putIfNotEmpty(target: JSONObject, key: String, value: JSONObject) {
        if (value.length() > 0) {
            target.put(key, value)
        }
    }

    private fun splitList(value: String): List<String> {
        return value.split(Regex("[,|]")).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
