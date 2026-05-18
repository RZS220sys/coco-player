package com.player.coco.share

import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.singlelink.SingleLinkConfigDataMapper
import com.player.coco.share.single.HttpShareCodec
import com.player.coco.share.single.ShadowsocksShareCodec
import com.player.coco.share.single.SingleLinkProtocolShareCodec
import com.player.coco.share.single.SocksShareCodec
import com.player.coco.share.single.TrojanShareCodec
import com.player.coco.share.single.VlessShareCodec
import com.player.coco.share.single.VmessShareCodec

object SingleLinkShareCodec : ConfigShareCodec {
    override val protocol: String = ConnectConfigTypes.SINGLE_LINK

    private val protocolCodecs: List<SingleLinkProtocolShareCodec> = listOf(
        VlessShareCodec,
        VmessShareCodec,
        TrojanShareCodec,
        ShadowsocksShareCodec,
        SocksShareCodec,
        HttpShareCodec,
    )

    override fun canDecode(link: String): Boolean {
        return protocolCodecs.any { it.canDecode(link) }
    }

    override fun encode(config: ConnectConfigContainer): String? {
        val singleLink = SingleLinkConfigDataMapper.fromContainer(config) ?: return null
        return protocolCodecs
            .firstOrNull { it.protocol == singleLink.protocol }
            ?.encode(singleLink.name, singleLink.values)
    }

    override fun decode(link: String): DecodedConfigDraft? {
        return try {
            protocolCodecs
                .firstOrNull { it.canDecode(link) }
                ?.decode(link)
                ?.let { DecodedSingleLinkDraft(it) }
        } catch (_: Exception) {
            null
        }
    }
}
