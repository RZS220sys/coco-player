package com.player.coco.ui.connect.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.ui.connect.single.SingleLinkCommonSections
import com.player.coco.ui.connect.single.SingleLinkField
import com.player.coco.ui.connect.single.SingleLinkSection
import com.player.coco.ui.connect.single.SingleLinkType
import com.player.coco.ui.connect.single.TextInput

object ShadowsocksSingleLinkType {
    val type = SingleLinkType(
        id = SingleLinkProtocols.SHADOWSOCKS,
        sections = listOf(
            SingleLinkCommonSections.server(defaultPort = "8388"),
            SingleLinkSection(
                id = SingleLinkValueKeys.SECTION_IDENTITY,
                title = "Identity",
                fields = listOf(
                    SingleLinkField.Text(
                        SingleLinkValueKeys.METHOD,
                        "Method",
                        defaultValue = "2022-blake3-aes-128-gcm",
                        required = true
                    ),
                    SingleLinkField.Text(
                        SingleLinkValueKeys.PASSWORD,
                        "Password",
                        required = true,
                        input = TextInput.PASSWORD
                    ),
                    SingleLinkField.Switch(SingleLinkValueKeys.UOT, "UDP over TCP", defaultChecked = true),
                )
            ),
        )
    )
}
