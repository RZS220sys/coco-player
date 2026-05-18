package com.player.coco.xray.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.xray.single.SingleLinkOutboundCommon
import org.json.JSONObject

object VmessOutboundBuilder {
    fun build(values: JSONObject): JSONObject {
        val identity = SingleLinkOutboundCommon.requiredSection(
            values,
            SingleLinkValueKeys.SECTION_IDENTITY,
            "Identity"
        )
        val user = JSONObject()
            .put("id", SingleLinkOutboundCommon.required(identity, SingleLinkValueKeys.ID, "User ID"))
            .put("alterId", identity.optString(SingleLinkValueKeys.ALTER_ID).toIntOrNull() ?: 0)
            .put("security", identity.optString(SingleLinkValueKeys.USER_SECURITY, "auto").ifBlank { "auto" })
            .put("level", 8)

        return JSONObject()
            .put("protocol", SingleLinkProtocols.VMESS)
            .put("settings", SingleLinkOutboundCommon.vnextSettings(values, user))
            .put("streamSettings", SingleLinkOutboundCommon.buildTransport(values))
    }
}
