package com.player.coco.xray.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkOutboundCommon
import org.json.JSONObject

object VlessOutboundBuilder {
    fun build(values: JSONObject): JSONObject {
        val identity = SingleLinkOutboundCommon.requiredSection(
            values,
            SingleLinkValueKeys.SECTION_IDENTITY,
            "Identity"
        )
        val user = JSONObject()
            .put("id", SingleLinkOutboundCommon.required(identity, SingleLinkValueKeys.ID, "User ID"))
            .put("encryption", identity.optString(SingleLinkValueKeys.ENCRYPTION, "none").ifBlank { "none" })
            .put("level", 8)
        SingleLinkOutboundCommon.putIfNotBlank(user, "flow", identity.optString(SingleLinkValueKeys.FLOW))

        return JSONObject()
            .put("protocol", SingleLinkProtocols.VLESS)
            .put("settings", SingleLinkOutboundCommon.vnextSettings(values, user))
            .put("streamSettings", SingleLinkOutboundCommon.buildTransport(values))
    }
}
