package com.player.coco.share.single

import android.net.Uri
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

object VlessShareCodec : SingleLinkProtocolShareCodec {
    override val protocol: String = SingleLinkProtocols.VLESS
    override val schemes: Set<String> = setOf("vless")

    override fun encode(name: String, values: JSONObject): String? {
        val identity = values.optJSONObject(SingleLinkValueKeys.SECTION_IDENTITY) ?: return null
        val params = linkedMapOf("encryption" to identity.optString(SingleLinkValueKeys.ENCRYPTION).ifBlank { "none" })
        putParamIfNotBlank(params, "flow", identity.optString(SingleLinkValueKeys.FLOW))
        putTransportParams(params, values, defaultSecurity = "none")
        return encodeAuthorityLink(
            scheme = "vless",
            userInfo = identity.optString(SingleLinkValueKeys.ID),
            name = name,
            values = values,
            params = params,
        )
    }

    override fun decode(link: String): SingleLinkDraft? {
        val parsed = Uri.parse(link.trim())
        val query = parseQuery(parsed.encodedQuery)
        val server = serverValues(parsed, defaultPort(query) { security ->
            if (security == "tls" || security == "reality") 443 else 80
        }) ?: return null
        val identity = JSONObject()
            .put(SingleLinkValueKeys.ID, decodeComponent(parsed.encodedUserInfo.orEmpty()))
            .put(SingleLinkValueKeys.ENCRYPTION, first(query, "encryption").ifBlank { "none" })
        putIfNotBlank(identity, SingleLinkValueKeys.FLOW, first(query, "flow"))

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
}
