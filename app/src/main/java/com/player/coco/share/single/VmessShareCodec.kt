package com.player.coco.share.single

import android.net.Uri
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkEndpoint
import org.json.JSONObject

object VmessShareCodec : SingleLinkProtocolShareCodec {
    override val protocol: String = SingleLinkProtocols.VMESS
    override val schemes: Set<String> = setOf("vmess")

    override fun encode(name: String, values: JSONObject): String? {
        val server = values.optJSONObject(SingleLinkValueKeys.SECTION_SERVER) ?: return null
        val identity = values.optJSONObject(SingleLinkValueKeys.SECTION_IDENTITY) ?: return null
        val transport = values.optJSONObject(SingleLinkValueKeys.SECTION_TRANSPORT)
        val network = normalizeNetwork(transport?.optString(SingleLinkValueKeys.NETWORK).orEmpty())
        val security = normalizeSecurity(transport?.optString(SingleLinkValueKeys.STREAM_SECURITY).orEmpty())

        val data = JSONObject()
            .put("v", "2")
            .put("ps", name)
            .put("add", server.optString(SingleLinkValueKeys.ADDRESS))
            .put("port", server.optString(SingleLinkValueKeys.PORT))
            .put("id", identity.optString(SingleLinkValueKeys.ID))
            .put("aid", identity.optString(SingleLinkValueKeys.ALTER_ID).ifBlank { "0" })
            .put("scy", identity.optString(SingleLinkValueKeys.USER_SECURITY).ifBlank { "auto" })
            .put("net", network.ifBlank { "tcp" })
            .put("type", "")
            .put("host", "")
            .put("path", "")
            .put("tls", security)
            .put("sni", "")
            .put("fp", "")
            .put("alpn", "")

        fillTransportJson(data, values, network.ifBlank { "tcp" }, security)
        return "vmess://${encodeBase64(data.toString(), removePadding = false)}"
    }

    override fun decode(link: String): SingleLinkDraft? {
        return if (link.contains("@") && link.contains("?")) {
            decodeStandard(link)
        } else {
            decodeJson(link)
        }
    }

    private fun decodeStandard(link: String): SingleLinkDraft? {
        val parsed = Uri.parse(link.trim())
        val query = parseQuery(parsed.encodedQuery)
        val server = serverValues(parsed, defaultPort(query) { security ->
            if (security == "tls" || security == "reality") 443 else 80
        }) ?: return null
        val identity = JSONObject()
            .put(SingleLinkValueKeys.ID, decodeComponent(parsed.encodedUserInfo.orEmpty()))
            .put(SingleLinkValueKeys.ALTER_ID, first(query, "alterId", "aid").ifBlank { "0" })
            .put(
                SingleLinkValueKeys.USER_SECURITY,
                first(query, "encryption", "scy", "userSecurity").ifBlank { "auto" }
            )

        if (identity.optString(SingleLinkValueKeys.ID).isBlank()) {
            return null
        }

        val values = JSONObject()
            .put(SingleLinkValueKeys.SECTION_SERVER, server)
            .put(SingleLinkValueKeys.SECTION_IDENTITY, identity)
        putTransportFromQuery(values, query, defaultSecurity = "none") ?: return null

        return draft(
            name = linkName(parsed, values),
            protocol = protocol,
            values = values,
        )
    }

    private fun decodeJson(link: String): SingleLinkDraft? {
        val payload = linkBody(link).substringBefore("#")
        val data = JSONObject(decodeBase64Text(payload))
        val host = data.optString("add").ifBlank { data.optString("address") }
        val id = data.optString("id")
        if (host.isBlank() || id.isBlank()) {
            return null
        }

        val values = JSONObject()
            .put(
                SingleLinkValueKeys.SECTION_SERVER,
                JSONObject()
                    .put(SingleLinkValueKeys.ADDRESS, host)
                    .put(SingleLinkValueKeys.PORT, data.optString("port").ifBlank { "443" })
            )
            .put(
                SingleLinkValueKeys.SECTION_IDENTITY,
                JSONObject()
                    .put(SingleLinkValueKeys.ID, id)
                    .put(SingleLinkValueKeys.ALTER_ID, data.optString("aid").ifBlank { "0" })
                    .put(
                        SingleLinkValueKeys.USER_SECURITY,
                        data.optString("scy").ifBlank { data.optString("security").ifBlank { "auto" } }
                    )
            )

        val query = linkedMapOf(
            "type" to data.optString("net").ifBlank { data.optString("type").ifBlank { "tcp" } },
            "security" to normalizeSecurity(data.optString("tls")),
            "sni" to data.optString("sni").ifBlank { data.optString("peer") },
            "alpn" to data.optString("alpn"),
            "fp" to data.optString("fp"),
            "host" to data.optString("host"),
            "path" to data.optString("path"),
            "mode" to data.optString("type"),
            "allowInsecure" to data.optString("insecure"),
        )
        putTransportFromQuery(values, query, defaultSecurity = "none") ?: return null

        return draft(
            name = data.optString("ps").ifBlank { SingleLinkEndpoint.fromValues(values) },
            protocol = protocol,
            values = values,
        )
    }

    private fun fillTransportJson(data: JSONObject, values: JSONObject, network: String, security: String) {
        if (security == "tls") {
            values.optJSONObject(SingleLinkValueKeys.SECTION_TLS)?.let { tls ->
                data.put("sni", tls.optString(SingleLinkValueKeys.SNI))
                data.put("fp", tls.optString(SingleLinkValueKeys.FINGERPRINT))
                data.put("alpn", tls.optString(SingleLinkValueKeys.ALPN))
                data.put("insecure", if (tls.optBoolean(SingleLinkValueKeys.ALLOW_INSECURE, false)) "1" else "0")
            }
        }

        when (network) {
            "ws" -> values.optJSONObject(SingleLinkValueKeys.SECTION_WEBSOCKET)?.let { ws ->
                data.put("host", ws.optString(SingleLinkValueKeys.HOST))
                data.put("path", ws.optString(SingleLinkValueKeys.PATH))
            }
            "grpc" -> values.optJSONObject(SingleLinkValueKeys.SECTION_GRPC)?.let { grpc ->
                data.put("host", grpc.optString(SingleLinkValueKeys.HOST))
                data.put("path", grpc.optString(SingleLinkValueKeys.PATH))
            }
            "xhttp" -> values.optJSONObject(SingleLinkValueKeys.SECTION_XHTTP)?.let { xhttp ->
                data.put("host", xhttp.optString(SingleLinkValueKeys.HOST))
                data.put("path", xhttp.optString(SingleLinkValueKeys.PATH))
                data.put("type", xhttp.optString(SingleLinkValueKeys.MODE))
            }
            "httpupgrade" -> values.optJSONObject(SingleLinkValueKeys.SECTION_HTTP_UPGRADE)?.let { httpUpgrade ->
                data.put("host", httpUpgrade.optString(SingleLinkValueKeys.HOST))
                data.put("path", httpUpgrade.optString(SingleLinkValueKeys.PATH))
            }
        }
    }
}
