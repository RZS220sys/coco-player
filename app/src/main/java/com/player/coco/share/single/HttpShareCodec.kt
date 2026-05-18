package com.player.coco.share.single

import android.net.Uri
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

object HttpShareCodec : SingleLinkProtocolShareCodec {
    override val protocol: String = SingleLinkProtocols.HTTP
    override val schemes: Set<String> = setOf("http")

    override fun canDecode(link: String): Boolean {
        val parsed = runCatching { Uri.parse(link.trim()) }.getOrNull() ?: return false
        val path = parsed.path.orEmpty()
        return parsed.scheme.equals("http", ignoreCase = true) &&
            parsed.host?.isNotBlank() == true &&
            parsed.port > 0 &&
            (path.isBlank() || path == "/")
    }

    override fun encode(name: String, values: JSONObject): String? {
        val auth = values.optJSONObject(SingleLinkValueKeys.SECTION_AUTHENTICATION)
        val username = auth?.optString(SingleLinkValueKeys.USERNAME).orEmpty()
        val password = auth?.optString(SingleLinkValueKeys.PASSWORD).orEmpty()
        val userInfo = if (username.isBlank() && password.isBlank()) "" else "$username:$password"
        return encodeAuthorityLink("http", userInfo, name, values, params = emptyMap())
    }

    override fun decode(link: String): SingleLinkDraft? {
        val parsed = Uri.parse(link.trim())
        val values = JSONObject()
            .put(SingleLinkValueKeys.SECTION_SERVER, serverValues(parsed, defaultPort = 8080) ?: return null)

        decodeRawUserPassword(parsed.encodedUserInfo.orEmpty())?.let { (username, password) ->
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
