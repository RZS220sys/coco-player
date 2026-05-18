package com.player.coco.share

import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.chainlink.ChainLinkDraft
import com.player.coco.data.config.singlelink.SingleLinkDraft

sealed interface DecodedConfigDraft {
    val protocol: String
}

data class DecodedChainLinkDraft(
    val chainLink: ChainLinkDraft,
) : DecodedConfigDraft {
    override val protocol: String = ConnectConfigTypes.CHAIN_LINK
}

data class DecodedSingleLinkDraft(
    val singleLink: SingleLinkDraft,
) : DecodedConfigDraft {
    override val protocol: String = ConnectConfigTypes.SINGLE_LINK
}

interface ConfigShareCodec {
    val protocol: String

    fun canDecode(link: String): Boolean {
        val linkProtocol = link.trim().substringBefore("://", missingDelimiterValue = "")
        return linkProtocol.equals(protocol, ignoreCase = true)
    }

    fun encode(config: ConnectConfigContainer): String?

    fun decode(link: String): DecodedConfigDraft?
}
