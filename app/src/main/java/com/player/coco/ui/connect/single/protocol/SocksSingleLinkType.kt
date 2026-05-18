package com.player.coco.ui.connect.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.ui.connect.single.SingleLinkCommonSections
import com.player.coco.ui.connect.single.SingleLinkType

object SocksSingleLinkType {
    val type = SingleLinkType(
        id = SingleLinkProtocols.SOCKS,
        sections = listOf(
            SingleLinkCommonSections.server(defaultPort = "1080"),
            SingleLinkCommonSections.usernamePassword(),
        )
    )
}
