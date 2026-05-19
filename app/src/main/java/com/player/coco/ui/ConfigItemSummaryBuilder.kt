package com.player.coco.ui

import android.net.Uri
import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.chainlink.ChainLinkConfigDataMapper
import com.player.coco.data.config.singlelink.SingleLinkConfigDataMapper

object ConfigItemSummaryBuilder {
    fun fromConfig(config: ConnectConfigContainer): ConfigItemSummary {
        return when (config.type) {
            ConnectConfigTypes.CHAIN_LINK -> chainLinkSummary(config)
            ConnectConfigTypes.SINGLE_LINK -> censurableSummary(
                SingleLinkConfigDataMapper.fromContainer(config)?.endpoint.orEmpty()
            )
            else -> censurableSummary(config.data.optString("endpoint"))
        }
    }

    private fun chainLinkSummary(config: ConnectConfigContainer): ConfigItemSummary {
        val chainLink = ChainLinkConfigDataMapper.fromContainer(config)
            ?: return ConfigItemSummary(emptyList())
        val parts = mutableListOf<ConfigItemSummaryPart>()

        hostPortFromUri(chainLink.subUrl)?.let {
            parts += ConfigItemSummaryPart(it, canCensor = true)
        }
        hostPortFromUri(chainLink.exitUri)?.let { exit ->
            if (parts.isNotEmpty()) {
                parts += ConfigItemSummaryPart(" -> ", canCensor = false)
            }
            parts += ConfigItemSummaryPart(exit, canCensor = true)
        }

        return ConfigItemSummary(parts)
    }

    private fun censurableSummary(text: String): ConfigItemSummary {
        return if (text.isBlank()) {
            ConfigItemSummary(emptyList())
        } else {
            ConfigItemSummary.of(ConfigItemSummaryPart(text, canCensor = true))
        }
    }

    private fun hostPortFromUri(uriText: String): String? {
        if (uriText.isBlank()) {
            return null
        }
        return try {
            val uri = Uri.parse(uriText)
            val host = uri.host.orEmpty().takeIf { it.isNotBlank() } ?: return null
            val port = uri.port
            if (port > 0) "$host : $port" else host
        } catch (_: Exception) {
            null
        }
    }
}
