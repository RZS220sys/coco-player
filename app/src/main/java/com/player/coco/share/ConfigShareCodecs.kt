package com.player.coco.share

import com.player.coco.data.config.ConnectConfigContainer

object ConfigShareCodecs {
    private val codecs = listOf<ConfigShareCodec>(
        ChainLinkShareCodec,
        SingleLinkShareCodec,
    )

    fun encode(config: ConnectConfigContainer): String? {
        return codecForConfig(config)?.encode(config)
    }

    fun decode(link: String): DecodedConfigDraft? {
        return codecForLink(link)?.decode(link)
    }

    fun codecForConfig(config: ConnectConfigContainer): ConfigShareCodec? {
        return codecs.firstOrNull { it.protocol == config.type }
    }

    fun codecForLink(link: String): ConfigShareCodec? {
        return codecs.firstOrNull { it.canDecode(link) }
    }

    fun codecForProtocol(protocol: String): ConfigShareCodec? {
        return codecs.firstOrNull { it.protocol == protocol }
    }
}
