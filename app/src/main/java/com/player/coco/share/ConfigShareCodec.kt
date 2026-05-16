package com.player.coco.share

import com.player.coco.data.ChainLinkConfig
import com.player.coco.data.ChainLinkDraft
import com.player.coco.data.ChainLinkStore

sealed interface DecodedConfigDraft {
    val protocol: String
}

data class DecodedChainLinkDraft(
    val chainLink: ChainLinkDraft,
) : DecodedConfigDraft {
    override val protocol: String = ChainLinkStore.TYPE_CHAIN_LINK
}

interface ConfigShareCodec {
    val protocol: String

    fun encode(config: ChainLinkConfig): String

    fun decode(link: String): DecodedConfigDraft?
}
