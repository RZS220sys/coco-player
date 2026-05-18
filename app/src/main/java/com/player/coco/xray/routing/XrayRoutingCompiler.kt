package com.player.coco.xray.routing

import com.player.coco.data.routing.RoutingProfile
import com.player.coco.data.routing.RoutingRule
import com.player.coco.data.routing.RoutingRuleOutbounds
import com.player.coco.data.routing.RoutingSettings
import org.json.JSONArray
import org.json.JSONObject

object XrayRoutingCompiler {
    fun domainStrategy(settings: RoutingSettings, fallback: String): String {
        return activeProfile(settings)
            ?.domainStrategy
            ?.ifBlank { fallback }
            ?: fallback
    }

    fun compileRules(
        settings: RoutingSettings,
        mainInboundTags: List<String>,
        proxyTag: String,
        proxyBalancerTag: String? = null,
        directTag: String,
        blockTag: String,
    ): JSONArray {
        val rules = JSONArray()
        val activeProfile = activeProfile(settings) ?: return rules
        val activeRules = activeProfile.rules
            .filter { it.enabled }
            .sortedBy { it.sort }
        activeRules
            .flatMap { rule ->
                compileRule(
                    rule = rule,
                    mainInboundTags = mainInboundTags,
                    proxyTag = proxyTag,
                    proxyBalancerTag = proxyBalancerTag,
                    directTag = directTag,
                    blockTag = blockTag,
                )
            }
            .forEach { rules.put(it) }
        return rules
    }

    private fun activeProfile(settings: RoutingSettings): RoutingProfile? {
        return settings.profiles.firstOrNull { it.id == settings.activeProfileId && it.enabled }
            ?: settings.profiles.filter { it.enabled }.minByOrNull { it.sort }
            ?: settings.profiles.minByOrNull { it.sort }
    }

    private fun compileRule(
        rule: RoutingRule,
        mainInboundTags: List<String>,
        proxyTag: String,
        proxyBalancerTag: String?,
        directTag: String,
        blockTag: String,
    ): List<JSONObject> {
        val outboundTag = when (rule.outboundTag) {
            RoutingRuleOutbounds.PROXY -> if (proxyBalancerTag == null) proxyTag else ""
            RoutingRuleOutbounds.DIRECT -> directTag
            RoutingRuleOutbounds.BLOCK -> blockTag
            else -> return emptyList()
        }
        val balancerTag = if (rule.outboundTag == RoutingRuleOutbounds.PROXY) proxyBalancerTag else null
        val inboundTags = scopedInboundTags(rule, mainInboundTags)
        if (inboundTags.isEmpty()) {
            return emptyList()
        }

        val domains = cleanValues(rule.domain)
        val ips = cleanValues(rule.ip)
        if (domains.isNotEmpty() && ips.isNotEmpty()) {
            return listOf(
                compileFieldRule(rule, inboundTags, outboundTag, balancerTag, domains = domains, ips = emptyList()),
                compileFieldRule(rule, inboundTags, outboundTag, balancerTag, domains = emptyList(), ips = ips),
            )
        }

        return listOf(compileFieldRule(rule, inboundTags, outboundTag, balancerTag, domains, ips))
    }

    private fun compileFieldRule(
        rule: RoutingRule,
        inboundTags: List<String>,
        outboundTag: String,
        balancerTag: String?,
        domains: List<String>,
        ips: List<String>,
    ): JSONObject {
        val compiled = JSONObject()
            .put("type", "field")
            .put("inboundTag", JSONArray(inboundTags))

        if (balancerTag == null) {
            compiled.put("outboundTag", outboundTag)
        } else {
            compiled.put("balancerTag", balancerTag)
        }

        rule.port.trim().takeIf { it.isNotBlank() }?.let { compiled.put("port", it) }
        normalizedNetwork(rule.network).takeIf { it.isNotBlank() }?.let { compiled.put("network", it) }
        putArray(compiled, "protocol", rule.protocol)
        putArray(compiled, "domain", domains)
        putArray(compiled, "ip", ips)

        return compiled
    }

    private fun scopedInboundTags(rule: RoutingRule, mainInboundTags: List<String>): List<String> {
        if (rule.inboundTag.isEmpty()) {
            return mainInboundTags
        }
        return rule.inboundTag
            .map { it.trim() }
            .filter { it in mainInboundTags }
            .distinct()
    }

    private fun normalizedNetwork(network: String): String {
        return when (network.trim().lowercase()) {
            "tcp", "udp", "tcp,udp" -> network.trim().lowercase()
            else -> ""
        }
    }

    private fun putArray(target: JSONObject, key: String, values: List<String>) {
        val cleanValues = cleanValues(values)
        if (cleanValues.isNotEmpty()) {
            target.put(key, JSONArray(cleanValues))
        }
    }

    private fun cleanValues(values: List<String>): List<String> {
        return values.map { it.trim() }.filter { it.isNotBlank() }
    }
}
