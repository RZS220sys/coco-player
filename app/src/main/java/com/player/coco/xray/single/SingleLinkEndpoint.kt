package com.player.coco.xray.single

import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

object SingleLinkEndpoint {
    fun fromValues(values: JSONObject): String {
        val server = values.optJSONObject(SingleLinkValueKeys.SECTION_SERVER)
        val address = server?.optString(SingleLinkValueKeys.ADDRESS)?.ifBlank { "?" } ?: "?"
        val port = server?.optString(SingleLinkValueKeys.PORT)?.toIntOrNull()
        return if (port != null && port > 0) {
            "${maskHost(address)} : $port"
        } else {
            maskHost(address)
        }
    }

    private fun maskHost(host: String): String {
        val parts = host.split(".")
        return if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            "${parts[0]}.${parts[1]}.${parts[2]}.***"
        } else {
            host
        }
    }
}
