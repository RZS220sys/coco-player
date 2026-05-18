package com.player.coco.ui.connect.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.ui.connect.single.SingleLinkCommonSections
import com.player.coco.ui.connect.single.SingleLinkField
import com.player.coco.ui.connect.single.SingleLinkSection
import com.player.coco.ui.connect.single.SingleLinkType
import com.player.coco.ui.connect.single.TextInput

object TrojanSingleLinkType {
    val type = SingleLinkType(
        id = SingleLinkProtocols.TROJAN,
        sections = listOf(
            SingleLinkCommonSections.server(defaultPort = "443"),
            SingleLinkSection(
                id = SingleLinkValueKeys.SECTION_IDENTITY,
                title = "Identity",
                fields = listOf(
                    SingleLinkField.Text(
                        SingleLinkValueKeys.PASSWORD,
                        "Password",
                        required = true,
                        input = TextInput.PASSWORD
                    ),
                    SingleLinkField.Text(SingleLinkValueKeys.FLOW, "Flow"),
                )
            ),
            SingleLinkCommonSections.transport(defaultSecurity = "tls"),
        ) + SingleLinkCommonSections.transportDetailSections()
    )
}
