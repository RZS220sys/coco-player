package com.player.coco.ui

data class ConfigItemSummary(
    val parts: List<ConfigItemSummaryPart>,
) {
    companion object {
        fun of(vararg parts: ConfigItemSummaryPart): ConfigItemSummary {
            return ConfigItemSummary(parts.toList())
        }
    }
}

data class ConfigItemSummaryPart(
    val text: String,
    val canCensor: Boolean,
)
