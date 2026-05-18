package com.player.coco.share.single

import android.net.Uri
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

object SocksShareCodec : SingleLinkProtocolShareCodec {
    override val protocol: String = SingleLinkProtocols.SOCKS
    override val schemes: Set<String> = setOf("socks", "socks5")

    override fun encode(name: String, values: JSONObject): String? {
        val auth = values.optJSONObject(SingleLinkValueKeys.SECTION_AUTHENTICATION)
        val userInfo = encodeBase64(
            "${auth?.optString(SingleLinkValueKeys.USERNAME).orEmpty()}:${auth?.optString(SingleLinkValueKeys.PASSWORD).orEmpty()}",
            removePadding = true,
        )
        return encodeAuthorityLink("socks", userInfo, name, values, params = emptyMap())
    }

    override fun decode(link: String): SingleLinkDraft? {
        val parsed = Uri.parse(link.trim())
        val values = JSONObject()
            .put(SingleLinkValueKeys.SECTION_SERVER, serverValues(parsed, defaultPort = 1080) ?: return null)

        decodeUserPassword(parsed.encodedUserInfo.orEmpty())?.let { (username, password) ->
            if (username.isNotBlank() || password.isNotBlank()) {
                values.put(
                    SingleLinkValueKeys.SECTION_AUTHENTICATION,
                    JSONObject()
                        .put(SingleLinkValueKeys.USERNAME, username)
                        .put(SingleLinkValueKeys.PASSWORD, password)
                )
            }
        }

        return draft(
            name = linkName(parsed, values),
            protocol = protocol,
            values = values,
        )
    }
}
