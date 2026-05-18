package com.player.coco.ui.connect.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.ui.connect.single.SingleLinkCommonSections
import com.player.coco.ui.connect.single.SingleLinkType

object HttpSingleLinkType {
    val type = SingleLinkType(
        id = SingleLinkProtocols.HTTP,
        sections = listOf(
            SingleLinkCommonSections.server(defaultPort = "8080"),
            SingleLinkCommonSections.usernamePassword(),
        )
    )
}
