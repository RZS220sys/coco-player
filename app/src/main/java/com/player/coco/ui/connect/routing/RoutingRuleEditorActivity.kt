package com.player.coco.ui.connect.routing

import com.player.coco.R
import com.player.coco.data.routing.RoutingRule
import com.player.coco.data.routing.RoutingRuleOutbounds
import com.player.coco.data.routing.RoutingGeoSuggestions
import com.player.coco.data.routing.RoutingSettings
import com.player.coco.data.routing.RoutingSettingsJson
import com.player.coco.data.routing.RoutingSettingsStore
import com.player.coco.ui.connect.finishIfConnectDataLocked
import com.player.coco.ui.widget.CocoCreatableTagField
import com.player.coco.ui.widget.CocoMultiChoiceField
import com.player.coco.ui.widget.CocoSelectField

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class RoutingRuleEditorActivity : Activity() {
    private lateinit var store: RoutingSettingsStore
    private lateinit var nameInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var outboundTagSelect: CocoSelectField
    private lateinit var portInput: EditText
    private lateinit var networkSelect: CocoSelectField
    private lateinit var protocolChoices: CocoMultiChoiceField
    private lateinit var inboundTagInput: EditText
    private lateinit var domainTags: CocoCreatableTagField
    private lateinit var ipTags: CocoCreatableTagField
    private var settings = RoutingSettings(
        version = RoutingSettingsJson.CURRENT_VERSION,
        activeProfileId = 0L,
        profiles = emptyList(),
    )
    private var profileId = 0L
    private var ruleId = 0L
    private var originalRule: RoutingRule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishIfConnectDataLocked()) {
            return
        }
        setContentView(R.layout.activity_routing_rule_editor)

        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
        ruleId = intent.getLongExtra(EXTRA_RULE_ID, 0L)
        store = RoutingSettingsStore(filesDir)
        nameInput = findViewById(R.id.rule_name_input)
        enabledSwitch = findViewById(R.id.rule_enabled_switch)
        outboundTagSelect = CocoSelectField.bind(
            field = findViewById(R.id.outbound_tag_select),
            options = resources.getStringArray(R.array.routing_rule_outbound_options).toList(),
        )
        portInput = findViewById(R.id.port_input)
        networkSelect = CocoSelectField.bind(
            field = findViewById(R.id.network_select),
            options = resources.getStringArray(R.array.routing_rule_network_options).toList(),
        )
        protocolChoices = CocoMultiChoiceField.bind(
            container = findViewById<LinearLayout>(R.id.protocol_choices),
            options = resources.getStringArray(R.array.routing_rule_protocol_options).toList(),
        )
        inboundTagInput = findViewById(R.id.inbound_tag_input)
        domainTags = CocoCreatableTagField.bind(
            container = findViewById<LinearLayout>(R.id.domain_tags),
            suggestions = resources.getStringArray(R.array.routing_rule_domain_suggestions).toList(),
            hint = getString(R.string.routing_rule_domain_hint),
        )
        ipTags = CocoCreatableTagField.bind(
            container = findViewById<LinearLayout>(R.id.ip_tags),
            suggestions = resources.getStringArray(R.array.routing_rule_ip_suggestions).toList(),
            hint = getString(R.string.routing_rule_ip_hint),
        )

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.save_button).setOnClickListener { saveRule() }

        loadRule()
        loadGeoSuggestions()
    }

    private fun loadRule() {
        settings = store.loadOrCreate()
        val profile = settings.profiles.firstOrNull { it.id == profileId }
        val rule = profile?.rules?.firstOrNull { it.id == ruleId }
        if (profile == null || rule == null) {
            Toast.makeText(this, R.string.routing_rule_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        originalRule = rule
        nameInput.setText(rule.name)
        enabledSwitch.isChecked = rule.enabled
        outboundTagSelect.setValue(rule.outboundTag.ifBlank { RoutingRuleOutbounds.PROXY })
        portInput.setText(rule.port)
        networkSelect.setValue(rule.network)
        protocolChoices.setValues(rule.protocol)
        inboundTagInput.setText(rule.inboundTag.joinToString("\n"))
        domainTags.setValues(rule.domain)
        ipTags.setValues(rule.ip)
    }

    private fun saveRule() {
        val original = originalRule ?: return
        val name = nameInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.routing_rule_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        val network = networkSelect.value.trim()
        if (network !in VALID_NETWORKS) {
            Toast.makeText(this, R.string.routing_rule_network_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        val outboundTag = outboundTagSelect.value.trim()
        if (outboundTag !in VALID_OUTBOUNDS) {
            Toast.makeText(this, R.string.routing_rule_outbound_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        val updatedRule = original.copy(
            name = name,
            enabled = enabledSwitch.isChecked,
            outboundTag = outboundTag,
            port = portInput.text?.toString().orEmpty().trim(),
            network = network,
            protocol = protocolChoices.values,
            inboundTag = splitList(inboundTagInput),
            domain = domainTags.values,
            ip = ipTags.values,
        )
        val updatedProfiles = settings.profiles.map { profile ->
            if (profile.id != profileId) {
                profile
            } else {
                profile.copy(
                    rules = profile.rules.map { rule ->
                        if (rule.id == updatedRule.id) updatedRule else rule
                    }
                )
            }
        }

        settings = settings.copy(profiles = updatedProfiles)
        originalRule = updatedRule
        store.save(settings)
        Toast.makeText(this, R.string.routing_rule_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun loadGeoSuggestions() {
        val appContext = applicationContext
        val fallbackDomainTags = resources.getStringArray(R.array.routing_rule_domain_suggestions).toList()
        val fallbackIpTags = resources.getStringArray(R.array.routing_rule_ip_suggestions).toList()
        Thread {
            val geoTags = RoutingGeoSuggestions.loadFromAssets(appContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                domainTags.setSuggestions(fallbackDomainTags + geoTags.domainTags)
                ipTags.setSuggestions(fallbackIpTags + geoTags.ipTags)
            }
        }.start()
    }

    private fun splitList(input: EditText): List<String> {
        return input.text?.toString().orEmpty()
            .split(Regex("[,\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.player.coco.extra.ROUTING_PROFILE_ID"
        const val EXTRA_RULE_ID = "com.player.coco.extra.ROUTING_RULE_ID"

        private val VALID_NETWORKS = setOf("", "tcp", "udp", "tcp,udp")
        private val VALID_OUTBOUNDS = setOf(
            RoutingRuleOutbounds.PROXY,
            RoutingRuleOutbounds.DIRECT,
            RoutingRuleOutbounds.BLOCK,
        )
    }
}
