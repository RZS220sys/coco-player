package com.player.coco.xray.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkOutboundCommon
import org.json.JSONArray
import org.json.JSONObject

object SocksOutboundBuilder {
    fun build(values: JSONObject): JSONObject {
        val server = SingleLinkOutboundCommon.baseServer(values)
        addUsersIfPresent(server, values)

        return JSONObject()
            .put("protocol", SingleLinkProtocols.SOCKS)
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }

    private fun addUsersIfPresent(server: JSONObject, values: JSONObject) {
        val auth = values.optJSONObject(SingleLinkValueKeys.SECTION_AUTHENTICATION) ?: return
        val user = auth.optString(SingleLinkValueKeys.USERNAME)
        val pass = auth.optString(SingleLinkValueKeys.PASSWORD)
        if (user.isNotBlank() || pass.isNotBlank()) {
            server.put(
                "users",
                JSONArray().put(
                    JSONObject()
                        .put("user", user)
                        .put("pass", pass)
                )
            )
        }
    }
}
