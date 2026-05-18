package com.player.coco.share

import com.player.coco.data.config.ConnectConfigContainer

object ConfigShareCodecs {
    private val codecsByProtocol = listOf<ConfigShareCodec>(
        ChainLinkShareCodec,
    ).associateBy { it.protocol }

    fun encode(config: ConnectConfigContainer): String? {
        return codecForConfig(config)?.encode(config)
    }

    fun decode(link: String): DecodedConfigDraft? {
        return codecForLink(link)?.decode(link)
    }

    fun codecForConfig(config: ConnectConfigContainer): ConfigShareCodec? {
        return codecForProtocol(config.type)
    }

    fun codecForLink(link: String): ConfigShareCodec? {
        val protocol = link.trim().substringBefore("://", missingDelimiterValue = "")
        return codecForProtocol(protocol)
    }

    fun codecForProtocol(protocol: String): ConfigShareCodec? {
        return codecsByProtocol[protocol]
    }
}
