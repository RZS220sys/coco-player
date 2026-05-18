package com.player.coco.share.single

import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

internal fun putTransportFromQuery(
    values: JSONObject,
    query: Map<String, String>,
    defaultSecurity: String,
): JSONObject? {
    val network = normalizeNetwork(first(query, "type", "net", "network").ifBlank { "tcp" })
    val security = normalizeSecurity(first(query, "security", "tls").ifBlank { defaultSecurity })

    values.put(
        SingleLinkValueKeys.SECTION_TRANSPORT,
        JSONObject()
            .put(SingleLinkValueKeys.STREAM_SECURITY, security)
            .put(SingleLinkValueKeys.NETWORK, network)
    )

    when (security) {
        "tls" -> putIfNotEmpty(
            values,
            SingleLinkValueKeys.SECTION_TLS,
            JSONObject().also { tls ->
                putIfNotBlank(tls, SingleLinkValueKeys.SNI, first(query, "sni", "serverName", "servername", "peer"))
                putIfNotBlank(tls, SingleLinkValueKeys.ALPN, first(query, "alpn"))
                putIfNotBlank(tls, SingleLinkValueKeys.FINGERPRINT, first(query, "fp", "fingerprint"))
                if (truthy(first(query, "allowInsecure", "allowinsecure", "insecure"))) {
                    tls.put(SingleLinkValueKeys.ALLOW_INSECURE, true)
                }
            }
        )
        "reality" -> {
            val publicKey = first(query, "pbk", "publicKey", "publickey")
            if (publicKey.isBlank()) {
                return null
            }
            values.put(
                SingleLinkValueKeys.SECTION_REALITY,
                JSONObject().also { reality ->
                    putIfNotBlank(reality, SingleLinkValueKeys.SNI, first(query, "sni", "serverName", "servername", "peer"))
                    putIfNotBlank(reality, SingleLinkValueKeys.FINGERPRINT, first(query, "fp", "fingerprint"))
                    reality.put(SingleLinkValueKeys.PUBLIC_KEY, publicKey)
                    putIfNotBlank(reality, SingleLinkValueKeys.SHORT_ID, first(query, "sid", "shortId", "shortid"))
                    putIfNotBlank(reality, SingleLinkValueKeys.SPIDER_X, first(query, "spx", "spiderX", "spiderx"))
                }
            )
        }
    }

    when (network) {
        "ws" -> putIfNotEmpty(
            values,
            SingleLinkValueKeys.SECTION_WEBSOCKET,
            JSONObject().also { ws ->
                putIfNotBlank(ws, SingleLinkValueKeys.HOST, first(query, "host"))
                putIfNotBlank(ws, SingleLinkValueKeys.PATH, first(query, "path"))
            }
        )
        "grpc" -> putIfNotEmpty(
            values,
            SingleLinkValueKeys.SECTION_GRPC,
            JSONObject().also { grpc ->
                putIfNotBlank(grpc, SingleLinkValueKeys.HOST, first(query, "authority", "host"))
                putIfNotBlank(grpc, SingleLinkValueKeys.PATH, first(query, "serviceName", "path"))
            }
        )
        "xhttp" -> putIfNotEmpty(
            values,
            SingleLinkValueKeys.SECTION_XHTTP,
            JSONObject().also { xhttp ->
                putIfNotBlank(xhttp, SingleLinkValueKeys.HOST, first(query, "host"))
                putIfNotBlank(xhttp, SingleLinkValueKeys.PATH, first(query, "path"))
                putIfNotBlank(xhttp, SingleLinkValueKeys.MODE, first(query, "mode"))
            }
        )
        "httpupgrade" -> putIfNotEmpty(
            values,
            SingleLinkValueKeys.SECTION_HTTP_UPGRADE,
            JSONObject().also { httpUpgrade ->
                putIfNotBlank(httpUpgrade, SingleLinkValueKeys.HOST, first(query, "host"))
                putIfNotBlank(httpUpgrade, SingleLinkValueKeys.PATH, first(query, "path"))
            }
        )
    }

    return values
}

internal fun putTransportParams(
    params: MutableMap<String, String>,
    values: JSONObject,
    defaultSecurity: String,
) {
    val transport = values.optJSONObject(SingleLinkValueKeys.SECTION_TRANSPORT)
    val security = normalizeSecurity(transport?.optString(SingleLinkValueKeys.STREAM_SECURITY).orEmpty())
        .ifBlank { defaultSecurity }
    val network = normalizeNetwork(transport?.optString(SingleLinkValueKeys.NETWORK).orEmpty()).ifBlank { "tcp" }
    params["security"] = security
    params["type"] = network

    when (security) {
        "tls" -> values.optJSONObject(SingleLinkValueKeys.SECTION_TLS)?.let { tls ->
            putParamIfNotBlank(params, "sni", tls.optString(SingleLinkValueKeys.SNI))
            putParamIfNotBlank(params, "alpn", tls.optString(SingleLinkValueKeys.ALPN))
            putParamIfNotBlank(params, "fp", tls.optString(SingleLinkValueKeys.FINGERPRINT))
            if (tls.optBoolean(SingleLinkValueKeys.ALLOW_INSECURE, false)) {
                params["allowInsecure"] = "1"
                params["insecure"] = "1"
            }
        }
        "reality" -> values.optJSONObject(SingleLinkValueKeys.SECTION_REALITY)?.let { reality ->
            putParamIfNotBlank(params, "sni", reality.optString(SingleLinkValueKeys.SNI))
            putParamIfNotBlank(params, "fp", reality.optString(SingleLinkValueKeys.FINGERPRINT))
            putParamIfNotBlank(params, "pbk", reality.optString(SingleLinkValueKeys.PUBLIC_KEY))
            putParamIfNotBlank(params, "sid", reality.optString(SingleLinkValueKeys.SHORT_ID))
            putParamIfNotBlank(params, "spx", reality.optString(SingleLinkValueKeys.SPIDER_X))
        }
    }

    when (network) {
        "tcp" -> params["headerType"] = "none"
        "ws" -> values.optJSONObject(SingleLinkValueKeys.SECTION_WEBSOCKET)?.let { ws ->
            putParamIfNotBlank(params, "host", ws.optString(SingleLinkValueKeys.HOST))
            putParamIfNotBlank(params, "path", ws.optString(SingleLinkValueKeys.PATH))
        }
        "grpc" -> values.optJSONObject(SingleLinkValueKeys.SECTION_GRPC)?.let { grpc ->
            putParamIfNotBlank(params, "authority", grpc.optString(SingleLinkValueKeys.HOST))
            putParamIfNotBlank(params, "serviceName", grpc.optString(SingleLinkValueKeys.PATH))
        }
        "xhttp" -> values.optJSONObject(SingleLinkValueKeys.SECTION_XHTTP)?.let { xhttp ->
            putParamIfNotBlank(params, "host", xhttp.optString(SingleLinkValueKeys.HOST))
            putParamIfNotBlank(params, "path", xhttp.optString(SingleLinkValueKeys.PATH))
            putParamIfNotBlank(params, "mode", xhttp.optString(SingleLinkValueKeys.MODE))
        }
        "httpupgrade" -> values.optJSONObject(SingleLinkValueKeys.SECTION_HTTP_UPGRADE)?.let { httpUpgrade ->
            putParamIfNotBlank(params, "host", httpUpgrade.optString(SingleLinkValueKeys.HOST))
            putParamIfNotBlank(params, "path", httpUpgrade.optString(SingleLinkValueKeys.PATH))
        }
    }
}
