package com.player.coco.xray.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkOutboundCommon
import org.json.JSONArray
import org.json.JSONObject

object ShadowsocksOutboundBuilder {
    fun build(values: JSONObject): JSONObject {
        val identity = SingleLinkOutboundCommon.requiredSection(
            values,
            SingleLinkValueKeys.SECTION_IDENTITY,
            "Identity"
        )
        val server = SingleLinkOutboundCommon.baseServer(values)
            .put("method", SingleLinkOutboundCommon.required(identity, SingleLinkValueKeys.METHOD, "Method"))
            .put("password", SingleLinkOutboundCommon.required(identity, SingleLinkValueKeys.PASSWORD, "Password"))
            .put("level", 8)
            .put("uot", identity.optBoolean(SingleLinkValueKeys.UOT, true))

        return JSONObject()
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }
}
