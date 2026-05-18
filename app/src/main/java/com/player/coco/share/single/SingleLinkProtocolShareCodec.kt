package com.player.coco.share.single

import com.player.coco.data.config.singlelink.SingleLinkDraft
import org.json.JSONObject

interface SingleLinkProtocolShareCodec {
    val protocol: String
    val schemes: Set<String>

    fun canDecode(link: String): Boolean {
        return schemeOf(link) in schemes
    }

    fun encode(name: String, values: JSONObject): String?

    fun decode(link: String): SingleLinkDraft?
}

internal fun schemeOf(link: String): String {
    return link.trim().substringBefore("://", missingDelimiterValue = "").lowercase()
}
