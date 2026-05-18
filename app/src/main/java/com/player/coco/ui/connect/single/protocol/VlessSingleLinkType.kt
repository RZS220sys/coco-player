package com.player.coco.ui.connect.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.ui.connect.single.SingleLinkCommonSections
import com.player.coco.ui.connect.single.SingleLinkField
import com.player.coco.ui.connect.single.SingleLinkSection
import com.player.coco.ui.connect.single.SingleLinkType

object VlessSingleLinkType {
    val type = SingleLinkType(
        id = SingleLinkProtocols.VLESS,
        sections = listOf(
            SingleLinkCommonSections.server(defaultPort = "443"),
            SingleLinkSection(
                id = SingleLinkValueKeys.SECTION_IDENTITY,
                title = "Identity",
                fields = listOf(
                    SingleLinkField.Text(SingleLinkValueKeys.ID, "User ID", required = true),
                    SingleLinkField.Text(SingleLinkValueKeys.ENCRYPTION, "Encryption", defaultValue = "none"),
                    SingleLinkField.Text(SingleLinkValueKeys.FLOW, "Flow"),
                )
            ),
            SingleLinkCommonSections.transport(),
        ) + SingleLinkCommonSections.transportDetailSections()
    )
}
