package com.player.coco.share.single

import android.net.Uri
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

object TrojanShareCodec : SingleLinkProtocolShareCodec {
    override val protocol: String = SingleLinkProtocols.TROJAN
    override val schemes: Set<String> = setOf("trojan")

    override fun encode(name: String, values: JSONObject): String? {
        val identity = values.optJSONObject(SingleLinkValueKeys.SECTION_IDENTITY) ?: return null
        val params = linkedMapOf<String, String>()
        putParamIfNotBlank(params, "flow", identity.optString(SingleLinkValueKeys.FLOW))
        putTransportParams(params, values, defaultSecurity = "tls")
        return encodeAuthorityLink(
            scheme = "trojan",
            userInfo = identity.optString(SingleLinkValueKeys.PASSWORD),
            name = name,
            values = values,
            params = params,
        )
    }

    override fun decode(link: String): SingleLinkDraft? {
        val parsed = Uri.parse(link.trim())
        val query = parseQuery(parsed.encodedQuery)
        val server = serverValues(parsed, defaultPort = 443) ?: return null
        val identity = JSONObject()
            .put(SingleLinkValueKeys.PASSWORD, decodeComponent(parsed.encodedUserInfo.orEmpty()))
        putIfNotBlank(identity, SingleLinkValueKeys.FLOW, first(query, "flow"))

        if (identity.optString(SingleLinkValueKeys.PASSWORD).isBlank()) {
            return null
        }

        val values = JSONObject()
            .put(SingleLinkValueKeys.SECTION_SERVER, server)
            .put(SingleLinkValueKeys.SECTION_IDENTITY, identity)
        putTransportFromQuery(values, query, defaultSecurity = "tls") ?: return null

        return draft(
            name = linkName(parsed, values),
            protocol = protocol,
            values = values,
        )
    }
}
