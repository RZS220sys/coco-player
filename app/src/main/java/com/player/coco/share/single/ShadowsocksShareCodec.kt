package com.player.coco.share.single

import android.net.Uri
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import org.json.JSONObject

object ShadowsocksShareCodec : SingleLinkProtocolShareCodec {
    override val protocol: String = SingleLinkProtocols.SHADOWSOCKS
    override val schemes: Set<String> = setOf("ss")

    override fun encode(name: String, values: JSONObject): String? {
        val identity = values.optJSONObject(SingleLinkValueKeys.SECTION_IDENTITY) ?: return null
        val userInfo = encodeBase64(
            "${identity.optString(SingleLinkValueKeys.METHOD)}:${identity.optString(SingleLinkValueKeys.PASSWORD)}",
            removePadding = true,
        )
        return encodeAuthorityLink("ss", userInfo, name, values, params = emptyMap())
    }

    override fun decode(link: String): SingleLinkDraft? {
        val parsed = Uri.parse(link.trim())
        val values = JSONObject()
        val identity = JSONObject()
        val server: JSONObject

        if (parsed.host.isNullOrBlank()) {
            val body = linkBody(link).substringBefore("#").substringBefore("?")
            val decoded = decodeBase64Text(body)
            val userAndServer = decoded.split("@", limit = 2)
            if (userAndServer.size != 2) {
                return null
            }
            fillIdentity(identity, userAndServer[0]) ?: return null
            server = serverValues(userAndServer[1], defaultPort = 8388) ?: return null
        } else {
            server = serverValues(parsed, defaultPort = 8388) ?: return null
            val decodedUser = decodeComponent(parsed.encodedUserInfo.orEmpty())
            val userInfo = if (":" in decodedUser) decodedUser else decodeBase64Text(decodedUser)
            fillIdentity(identity, userInfo) ?: return null
        }

        values
            .put(SingleLinkValueKeys.SECTION_SERVER, server)
            .put(SingleLinkValueKeys.SECTION_IDENTITY, identity)

        return draft(
            name = linkName(parsed, values),
            protocol = protocol,
            values = values,
        )
    }

    private fun fillIdentity(identity: JSONObject, userInfo: String): JSONObject? {
        val parts = userInfo.split(":", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null
        }
        identity
            .put(SingleLinkValueKeys.METHOD, parts[0].lowercase())
            .put(SingleLinkValueKeys.PASSWORD, parts[1])
            .put(SingleLinkValueKeys.UOT, true)
        return identity
    }
}
