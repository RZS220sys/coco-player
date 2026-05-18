package com.player.coco.data.routing

import org.json.JSONObject

object RoutingDefaults {
    fun fromRoutingDns(routingDns: JSONObject): RoutingSettings {
        val profile = RoutingProfile(
            id = DEFAULT_PROFILE_ID,
            name = "Default Routing",
            enabled = true,
            locked = false,
            sort = 10,
            domainStrategy = routingDns.optString("domainStrategy", RoutingSettingsJson.DEFAULT_DOMAIN_STRATEGY)
                .ifBlank { RoutingSettingsJson.DEFAULT_DOMAIN_STRATEGY },
            domainMatcher = "",
            sourceUrl = "",
            customIcon = "",
            rules = defaultRules(routingDns),
        )
        return RoutingSettings(
            version = RoutingSettingsJson.CURRENT_VERSION,
            activeProfileId = profile.id,
            profiles = listOf(profile),
        )
    }

    fun upgradeLegacyDefault(settings: RoutingSettings): RoutingSettings {
        val upgradedProfiles = settings.profiles.map { profile ->
            if (isLegacyDefaultProfile(profile)) {
                profile.copy(rules = upgradedLegacyDefaultRules(profile.rules))
            } else {
                profile
            }
        }
        return settings.copy(profiles = upgradedProfiles)
    }

    fun defaultUserRules(startId: Long): List<RoutingRule> {
        return listOf(
            privateRule(startId, sort = 10),
            iranRule(startId + 1, sort = 20),
            adsRule(startId + 2, sort = 30),
            defaultProxyFallbackRule(startId + 3),
        )
    }

    fun defaultProxyFallbackRule(id: Long): RoutingRule {
        return RoutingRule(
            id = id,
            name = "Everything else",
            enabled = true,
            sort = 9999,
            outboundTag = RoutingRuleOutbounds.PROXY,
            port = "",
            network = "",
            protocol = emptyList(),
            inboundTag = emptyList(),
            domain = emptyList(),
            ip = emptyList(),
        )
    }

    private fun defaultRules(routingDns: JSONObject): List<RoutingRule> {
        val rules = mutableListOf<RoutingRule>()

        if (routingDns.optBoolean("blockUdp443", false)) {
            rules += RoutingRule(
                id = RULE_BLOCK_UDP443_ID,
                name = "Block UDP/443",
                enabled = true,
                sort = 10,
                outboundTag = RoutingRuleOutbounds.BLOCK,
                port = "443",
                network = "udp",
                protocol = emptyList(),
                inboundTag = emptyList(),
                domain = emptyList(),
                ip = emptyList(),
            )
        }

        if (routingDns.optBoolean("bypassPrivate", true)) {
            rules += privateRule(RULE_PRIVATE_ID, sort = 20)
        }
        rules += iranRule(RULE_IRAN_ID, sort = 30)
        rules += adsRule(RULE_ADS_ID, sort = 40)
        rules += defaultProxyFallbackRule(RULE_FALLBACK_ID)

        return rules
    }

    private fun privateRule(id: Long, sort: Int): RoutingRule {
        return RoutingRule(
            id = id,
            name = "Private domains and IPs",
            enabled = true,
            sort = sort,
            outboundTag = RoutingRuleOutbounds.DIRECT,
            port = "",
            network = "",
            protocol = emptyList(),
            inboundTag = emptyList(),
            domain = listOf("geosite:private"),
            ip = listOf("geoip:private"),
        )
    }

    private fun iranRule(id: Long, sort: Int): RoutingRule {
        return RoutingRule(
            id = id,
            name = "Iran domains and IPs",
            enabled = true,
            sort = sort,
            outboundTag = RoutingRuleOutbounds.DIRECT,
            port = "",
            network = "",
            protocol = emptyList(),
            inboundTag = emptyList(),
            domain = listOf("geosite:ir"),
            ip = listOf("geoip:ir"),
        )
    }

    private fun adsRule(id: Long, sort: Int): RoutingRule {
        return RoutingRule(
            id = id,
            name = "Ads",
            enabled = true,
            sort = sort,
            outboundTag = RoutingRuleOutbounds.BLOCK,
            port = "",
            network = "",
            protocol = emptyList(),
            inboundTag = emptyList(),
            domain = listOf(
                "geosite:category-ads-all",
                "geosite:category-ads-ir",
            ),
            ip = emptyList(),
        )
    }

    private fun isLegacyDefaultProfile(profile: RoutingProfile): Boolean {
        if (profile.id != DEFAULT_PROFILE_ID || profile.name != "Default Routing") {
            return false
        }
        val rules = profile.rules.sortedBy { it.sort }
        val relevantRules = rules.filterNot { it.id == RULE_BLOCK_UDP443_ID && it.name == "Block UDP/443" }
        if (relevantRules.size != 3) {
            return false
        }
        return isLegacyPrivateDomainRule(relevantRules[0]) &&
            isLegacyPrivateIpRule(relevantRules[1]) &&
            isFallbackRule(relevantRules[2])
    }

    private fun upgradedLegacyDefaultRules(oldRules: List<RoutingRule>): List<RoutingRule> {
        val rules = mutableListOf<RoutingRule>()
        oldRules.firstOrNull { it.id == RULE_BLOCK_UDP443_ID && it.name == "Block UDP/443" }?.let { rules += it }
        rules += privateRule(RULE_PRIVATE_ID, sort = 20)
        rules += iranRule(RULE_IRAN_ID, sort = 30)
        rules += adsRule(RULE_ADS_ID, sort = 40)
        rules += defaultProxyFallbackRule(RULE_FALLBACK_ID)
        return rules.sortedBy { it.sort }
    }

    private fun isLegacyPrivateDomainRule(rule: RoutingRule): Boolean {
        return rule.id == RULE_PRIVATE_ID &&
            rule.name == "Private domains" &&
            rule.outboundTag == RoutingRuleOutbounds.DIRECT &&
            rule.domain == listOf("geosite:private") &&
            rule.ip.isEmpty()
    }

    private fun isLegacyPrivateIpRule(rule: RoutingRule): Boolean {
        return rule.id == RULE_LEGACY_PRIVATE_IP_ID &&
            rule.name == "Private IPs" &&
            rule.outboundTag == RoutingRuleOutbounds.DIRECT &&
            rule.domain.isEmpty() &&
            rule.ip == listOf("geoip:private")
    }

    private fun isFallbackRule(rule: RoutingRule): Boolean {
        return rule.id == RULE_FALLBACK_ID &&
            rule.name == "Everything else" &&
            rule.outboundTag == RoutingRuleOutbounds.PROXY &&
            rule.domain.isEmpty() &&
            rule.ip.isEmpty()
    }

    private const val DEFAULT_PROFILE_ID = 1L
    private const val RULE_BLOCK_UDP443_ID = 1001L
    private const val RULE_PRIVATE_ID = 1002L
    private const val RULE_IRAN_ID = 1003L
    private const val RULE_ADS_ID = 1004L
    private const val RULE_LEGACY_PRIVATE_IP_ID = 1003L
    private const val RULE_FALLBACK_ID = 1099L
}
