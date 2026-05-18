package com.player.coco.xray.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkOutboundCommon
import org.json.JSONArray
import org.json.JSONObject

object TrojanOutboundBuilder {
    fun build(values: JSONObject): JSONObject {
        val identity = SingleLinkOutboundCommon.requiredSection(
            values,
            SingleLinkValueKeys.SECTION_IDENTITY,
            "Identity"
        )
        val server = SingleLinkOutboundCommon.baseServer(values)
            .put("password", SingleLinkOutboundCommon.required(identity, SingleLinkValueKeys.PASSWORD, "Password"))
            .put("level", 8)
        SingleLinkOutboundCommon.putIfNotBlank(server, "flow", identity.optString(SingleLinkValueKeys.FLOW))

        return JSONObject()
            .put("protocol", SingleLinkProtocols.TROJAN)
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", SingleLinkOutboundCommon.buildTransport(values, defaultSecurity = "tls"))
    }
}
