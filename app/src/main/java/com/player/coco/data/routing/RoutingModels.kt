package com.player.coco.data.routing

data class RoutingSettings(
    val version: Int,
    val activeProfileId: Long,
    val profiles: List<RoutingProfile>,
)

data class RoutingProfile(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val locked: Boolean,
    val sort: Int,
    val domainStrategy: String,
    val domainMatcher: String,
    val sourceUrl: String,
    val customIcon: String,
    val rules: List<RoutingRule>,
)

data class RoutingRule(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val sort: Int,
    val outboundTag: String,
    val port: String,
    val network: String,
    val protocol: List<String>,
    val inboundTag: List<String>,
    val domain: List<String>,
    val ip: List<String>,
)

object RoutingRuleOutbounds {
    const val PROXY = "proxy"
    const val DIRECT = "direct"
    const val BLOCK = "block"
}
