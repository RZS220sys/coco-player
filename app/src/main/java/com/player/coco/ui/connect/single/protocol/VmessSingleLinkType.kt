package com.player.coco.ui.connect.single.protocol

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.data.config.singlelink.SingleLinkValueKeys
import com.player.coco.ui.connect.single.SingleLinkCommonSections
import com.player.coco.ui.connect.single.SingleLinkField
import com.player.coco.ui.connect.single.SingleLinkSection
import com.player.coco.ui.connect.single.SingleLinkType
import com.player.coco.ui.connect.single.TextInput

object VmessSingleLinkType {
    val type = SingleLinkType(
        id = SingleLinkProtocols.VMESS,
        sections = listOf(
            SingleLinkCommonSections.server(defaultPort = "443"),
            SingleLinkSection(
                id = SingleLinkValueKeys.SECTION_IDENTITY,
                title = "Identity",
                fields = listOf(
                    SingleLinkField.Text(SingleLinkValueKeys.ID, "User ID", required = true),
                    SingleLinkField.Text(SingleLinkValueKeys.ALTER_ID, "Alter ID", defaultValue = "0", input = TextInput.NUMBER),
                    SingleLinkField.Select(
                        SingleLinkValueKeys.USER_SECURITY,
                        "User security",
                        listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none"),
                    ),
                )
            ),
            SingleLinkCommonSections.transport(),
        ) + SingleLinkCommonSections.transportDetailSections()
    )
}
