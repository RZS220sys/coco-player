package com.player.coco.xray.single

import com.player.coco.data.config.singlelink.SingleLinkProtocols
import com.player.coco.xray.single.protocol.HttpOutboundBuilder
import com.player.coco.xray.single.protocol.ShadowsocksOutboundBuilder
import com.player.coco.xray.single.protocol.SocksOutboundBuilder
import com.player.coco.xray.single.protocol.TrojanOutboundBuilder
import com.player.coco.xray.single.protocol.VlessOutboundBuilder
import com.player.coco.xray.single.protocol.VmessOutboundBuilder
import org.json.JSONObject

object SingleLinkOutboundBuilder {
    fun build(protocol: String, values: JSONObject): JSONObject {
        return when (protocol) {
            SingleLinkProtocols.VLESS -> VlessOutboundBuilder.build(values)
            SingleLinkProtocols.VMESS -> VmessOutboundBuilder.build(values)
            SingleLinkProtocols.TROJAN -> TrojanOutboundBuilder.build(values)
            SingleLinkProtocols.SHADOWSOCKS -> ShadowsocksOutboundBuilder.build(values)
            SingleLinkProtocols.SOCKS -> SocksOutboundBuilder.build(values)
            SingleLinkProtocols.HTTP -> HttpOutboundBuilder.build(values)
            else -> error("Unsupported single-link protocol: $protocol")
        }
    }
}
