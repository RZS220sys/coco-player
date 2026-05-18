package com.player.coco.data.routing

import org.json.JSONArray
import org.json.JSONObject

object RoutingSettingsJson {
    fun fromJson(json: JSONObject): RoutingSettings {
        val profiles = json.optJSONArray(KEY_PROFILES).toProfiles()
        val activeProfileId = json.optLong(KEY_ACTIVE_PROFILE_ID).takeIf { id ->
            profiles.any { it.id == id }
        } ?: profiles.firstOrNull()?.id ?: 0L

        return RoutingSettings(
            version = json.optInt(KEY_VERSION, CURRENT_VERSION),
            activeProfileId = activeProfileId,
            profiles = profiles,
        )
    }

    fun toJson(settings: RoutingSettings): JSONObject {
        return JSONObject()
            .put(KEY_VERSION, settings.version)
            .put(KEY_ACTIVE_PROFILE_ID, settings.activeProfileId)
            .put(KEY_PROFILES, JSONArray(settings.profiles.map { profileToJson(it) }))
    }

    private fun JSONArray?.toProfiles(): List<RoutingProfile> {
        if (this == null) {
            return emptyList()
        }

        val profiles = mutableListOf<RoutingProfile>()
        for (index in 0 until length()) {
            optJSONObject(index)?.toProfile()?.let { profiles.add(it) }
        }
        return profiles.sortedBy { it.sort }
    }

    private fun JSONObject.toProfile(): RoutingProfile? {
        val id = optLong(KEY_ID, 0L)
        if (id <= 0L) {
            return null
        }

        return RoutingProfile(
            id = id,
            name = optString(KEY_NAME).ifBlank { "Routing Profile" },
            enabled = optBoolean(KEY_ENABLED, true),
            locked = optBoolean(KEY_LOCKED, false),
            sort = optInt(KEY_SORT, 0),
            domainStrategy = optString(KEY_DOMAIN_STRATEGY, DEFAULT_DOMAIN_STRATEGY)
                .ifBlank { DEFAULT_DOMAIN_STRATEGY },
            domainMatcher = optString(KEY_DOMAIN_MATCHER),
            sourceUrl = optString(KEY_SOURCE_URL),
            customIcon = optString(KEY_CUSTOM_ICON),
            rules = optJSONArray(KEY_RULES).toRules(),
        )
    }

    private fun JSONArray?.toRules(): List<RoutingRule> {
        if (this == null) {
            return emptyList()
        }

        val rules = mutableListOf<RoutingRule>()
        for (index in 0 until length()) {
            optJSONObject(index)?.toRule()?.let { rules.add(it) }
        }
        return rules.sortedBy { it.sort }
    }

    private fun JSONObject.toRule(): RoutingRule? {
        val id = optLong(KEY_ID, 0L)
        if (id <= 0L) {
            return null
        }

        return RoutingRule(
            id = id,
            name = optString(KEY_NAME).ifBlank { "Routing Rule" },
            enabled = optBoolean(KEY_ENABLED, true),
            sort = optInt(KEY_SORT, 0),
            outboundTag = optString(KEY_OUTBOUND_TAG).ifBlank { RoutingRuleOutbounds.PROXY },
            port = optString(KEY_PORT),
            network = optString(KEY_NETWORK),
            protocol = optJSONArray(KEY_PROTOCOL).toStringList(),
            inboundTag = optJSONArray(KEY_INBOUND_TAG).toStringList(),
            domain = optJSONArray(KEY_DOMAIN).toStringList(),
            ip = optJSONArray(KEY_IP).toStringList(),
        )
    }

    private fun profileToJson(profile: RoutingProfile): JSONObject {
        return JSONObject()
            .put(KEY_ID, profile.id)
            .put(KEY_NAME, profile.name)
            .put(KEY_ENABLED, profile.enabled)
            .put(KEY_LOCKED, profile.locked)
            .put(KEY_SORT, profile.sort)
            .put(KEY_DOMAIN_STRATEGY, profile.domainStrategy)
            .put(KEY_DOMAIN_MATCHER, profile.domainMatcher)
            .put(KEY_SOURCE_URL, profile.sourceUrl)
            .put(KEY_CUSTOM_ICON, profile.customIcon)
            .put(KEY_RULES, JSONArray(profile.rules.map { ruleToJson(it) }))
    }

    private fun ruleToJson(rule: RoutingRule): JSONObject {
        return JSONObject()
            .put(KEY_ID, rule.id)
            .put(KEY_NAME, rule.name)
            .put(KEY_ENABLED, rule.enabled)
            .put(KEY_SORT, rule.sort)
            .put(KEY_OUTBOUND_TAG, rule.outboundTag)
            .put(KEY_PORT, rule.port)
            .put(KEY_NETWORK, rule.network)
            .put(KEY_PROTOCOL, JSONArray(rule.protocol))
            .put(KEY_INBOUND_TAG, JSONArray(rule.inboundTag))
            .put(KEY_DOMAIN, JSONArray(rule.domain))
            .put(KEY_IP, JSONArray(rule.ip))
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }

        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotBlank() }?.let { values.add(it) }
        }
        return values
    }

    const val CURRENT_VERSION = 1
    const val DEFAULT_DOMAIN_STRATEGY = "IPIfNonMatch"

    private const val KEY_VERSION = "version"
    private const val KEY_ACTIVE_PROFILE_ID = "activeProfileId"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LOCKED = "locked"
    private const val KEY_SORT = "sort"
    private const val KEY_DOMAIN_STRATEGY = "domainStrategy"
    private const val KEY_DOMAIN_MATCHER = "domainMatcher"
    private const val KEY_SOURCE_URL = "sourceUrl"
    private const val KEY_CUSTOM_ICON = "customIcon"
    private const val KEY_RULES = "rules"
    private const val KEY_OUTBOUND_TAG = "outboundTag"
    private const val KEY_PORT = "port"
    private const val KEY_NETWORK = "network"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_INBOUND_TAG = "inboundTag"
    private const val KEY_DOMAIN = "domain"
    private const val KEY_IP = "ip"
}
