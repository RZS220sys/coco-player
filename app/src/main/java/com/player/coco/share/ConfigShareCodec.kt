package com.player.coco.share

import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.chainlink.ChainLinkDraft

sealed interface DecodedConfigDraft {
    val protocol: String
}

data class DecodedChainLinkDraft(
    val chainLink: ChainLinkDraft,
) : DecodedConfigDraft {
    override val protocol: String = ConnectConfigTypes.CHAIN_LINK
}

interface ConfigShareCodec {
    val protocol: String

    fun encode(config: ConnectConfigContainer): String?

    fun decode(link: String): DecodedConfigDraft?
}
