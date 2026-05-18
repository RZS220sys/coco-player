package com.player.coco.ui.connect

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.player.coco.R
import com.player.coco.data.config.ConnectConfigStore
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.chainlink.ChainLinkConfigDataMapper
import com.player.coco.data.config.chainlink.ChainLinkDraft
import com.player.coco.data.GlobalSettingsStore
import com.player.coco.ui.getColorCompat
import com.player.coco.ui.widget.CocoSelectField
import org.json.JSONObject

class ChainLinkFormActivity : Activity() {
    private lateinit var localInboundsSection: View
    private lateinit var routingDnsSection: View
    private lateinit var runtimeSection: View
    private lateinit var localInboundsStatus: TextView
    private lateinit var routingDnsStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var clearLocalInboundsButton: ImageButton
    private lateinit var clearRoutingDnsButton: ImageButton
    private lateinit var clearRuntimeButton: ImageButton
    private lateinit var globalOverridesButton: TextView
    private lateinit var inboundsSelect: CocoSelectField
    private lateinit var observerSelect: CocoSelectField
    private lateinit var probeMethodSelect: CocoSelectField
    private lateinit var balancerStrategySelect: CocoSelectField
    private lateinit var domainStrategySelect: CocoSelectField
    private lateinit var loglevelSelect: CocoSelectField
    private lateinit var store: ConnectConfigStore
    private lateinit var globalSettingsStore: GlobalSettingsStore
    private var globalSettings = JSONObject()
    private var localInboundsOverridden = false
    private var routingDnsOverridden = false
    private var runtimeOverridden = false
    private var overrideSectionsExpanded = false
    private var loadingOverrideSections = false
    private var configId = NO_CONFIG_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chain_link_form)

        store = ConnectConfigStore(filesDir)
        globalSettingsStore = GlobalSettingsStore(filesDir)
        globalSettings = globalSettingsStore.load()
        configId = intent.getLongExtra(EXTRA_CONFIG_ID, NO_CONFIG_ID)
        localInboundsSection = findViewById(R.id.local_inbounds_section)
        routingDnsSection = findViewById(R.id.routing_dns_section)
        runtimeSection = findViewById(R.id.runtime_section)
        localInboundsStatus = findViewById(R.id.local_inbounds_override_status)
        routingDnsStatus = findViewById(R.id.routing_dns_override_status)
        runtimeStatus = findViewById(R.id.runtime_override_status)
        clearLocalInboundsButton = findViewById(R.id.clear_local_inbounds_override)
        clearRoutingDnsButton = findViewById(R.id.clear_routing_dns_override)
        clearRuntimeButton = findViewById(R.id.clear_runtime_override)
        globalOverridesButton = findViewById(R.id.global_overrides_button)
        inboundsSelect = bindSelect(R.id.inbounds_select, R.array.inbound_type_options)
        observerSelect = bindSelect(R.id.observer_select, R.array.observer_options)
        probeMethodSelect = bindSelect(R.id.probe_method_select, R.array.probe_method_options)
        balancerStrategySelect = CocoSelectField.bind(
            field = findViewById(R.id.balancer_strategy_select),
            options = resources.getStringArray(R.array.balancer_strategy_options).toList(),
        )
        domainStrategySelect = bindSelect(R.id.domain_strategy_select, R.array.domain_strategy_options)
        loglevelSelect = bindSelect(R.id.loglevel_select, R.array.loglevel_options)
        moveOverrideSectionsToBottom()
        renderTitle()

        findViewById<View>(R.id.back_button).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.save_button).setOnClickListener {
            saveChainLink()
        }

        bindClearOverrideButtons()
        globalOverridesButton.setOnClickListener {
            overrideSectionsExpanded = !overrideSectionsExpanded
            renderOverrideSectionsVisibility()
        }

        if (isEditMode()) {
            loadExistingChainLink()
        } else {
            populateSettings(JSONObject())
        }

        bindOverrideChangeTracking()
        renderOverrideStatuses()
        renderOverrideSectionsVisibility()
    }

    private fun moveOverrideSectionsToBottom() {
        val formContent = localInboundsSection.parent as? LinearLayout ?: return
        formContent.removeView(localInboundsSection)
        val routingIndex = formContent.indexOfChild(routingDnsSection)
        formContent.addView(localInboundsSection, routingIndex.coerceAtLeast(0))
    }

    private fun saveChainLink() {
        val draft = buildDraftOrNull() ?: return
        if (isEditMode()) {
            store.saveExisting(configId, ConnectConfigTypes.CHAIN_LINK, ChainLinkConfigDataMapper.toJson(draft, existingCreatedAtMillis()))
            Toast.makeText(this, getString(R.string.chain_link_updated), Toast.LENGTH_SHORT).show()
        } else {
            store.saveNew(ConnectConfigTypes.CHAIN_LINK, ChainLinkConfigDataMapper.toJson(draft))
            Toast.makeText(this, getString(R.string.chain_link_saved), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun buildDraftOrNull(): ChainLinkDraft? {
        val name = requiredText(R.id.chain_name_input, R.string.error_config_name_required)
            ?: return null
        val subUrl = requiredText(R.id.sub_url_input, R.string.error_sub_url_required)
            ?: return null
        val exitUri = textValue(R.id.exit_uri_input)

        return ChainLinkDraft(
            name = name,
            subUrl = subUrl,
            exitUri = exitUri,
            endpoint = endpointFromExitUri(exitUri),
            settings = buildSettingsJson(),
        )
    }

    private fun buildSettingsJson(): JSONObject {
        val settings = JSONObject()
            .put("generatedConfigName", ConnectConfigStore.GENERATED_XRAY_CONFIG_NAME)
            .put("subUpdateInterval", textValue(R.id.update_interval_input))
            .put("fetchTimeout", textValue(R.id.fetch_timeout_input))
            .put("maxFronts", textValue(R.id.max_fronts_input))
            .put("strict", switchValue(R.id.strict_switch))
            .put("observer", observerSelect.value)
            .put("probeUrl", textValue(R.id.probe_url_input))
            .put("connectivityUrl", textValue(R.id.connectivity_url_input))
            .put("probeInterval", textValue(R.id.probe_interval_input))
            .put("probeTimeout", textValue(R.id.probe_timeout_input))
            .put("probeSampling", textValue(R.id.probe_sampling_input))
            .put("probeMethod", probeMethodSelect.value)
            .put("balancerStrategy", balancerStrategySelect.value)
            .put("expected", textValue(R.id.expected_input))
            .put("maxRtt", textValue(R.id.max_rtt_input))
            .put("baseline", textValue(R.id.baseline_input))

        if (localInboundsOverridden) {
            settings.put("localInbounds", localInboundsJson())
        }
        if (routingDnsOverridden) {
            settings.put("routingDns", routingDnsJson())
        }
        if (runtimeOverridden) {
            settings.put("runtime", runtimeJson())
        }

        return settings
    }

    private fun localInboundsJson(): JSONObject {
        return JSONObject()
            .put("listen", textValue(R.id.listen_input))
            .put("inbounds", inboundsSelect.value)
            .put("socksPort", textValue(R.id.socks_port_input))
            .put("httpPort", textValue(R.id.http_port_input))
            .put("internalPort", textValue(R.id.internal_port_input))
    }

    private fun routingDnsJson(): JSONObject {
        return JSONObject()
            .put("domainStrategy", domainStrategySelect.value)
            .put("dnsServers", dnsServersValue(R.id.dns_servers_input))
            .put("domesticDnsServers", dnsServersValue(R.id.domestic_dns_servers_input))
            .put("useIpv4", switchValue(R.id.use_ipv4_switch))
            .put("bypassPrivate", switchValue(R.id.bypass_private_switch))
            .put("blockUdp443", switchValue(R.id.block_udp443_switch))
            .put("sniffRouteOnly", switchValue(R.id.sniff_route_only_switch))
    }

    private fun runtimeJson(): JSONObject {
        return JSONObject()
            .put("xrayBin", textValue(R.id.xray_bin_input))
            .put("loglevel", loglevelSelect.value)
            .put("noRun", switchValue(R.id.no_run_switch))
            .put("once", switchValue(R.id.once_switch))
            .put("printConfig", switchValue(R.id.print_config_switch))
    }

    private fun requiredText(inputId: Int, errorRes: Int): String? {
        val input = findViewById<EditText>(inputId)
        val value = input.text?.toString().orEmpty().trim()
        if (value.isEmpty()) {
            input.error = getString(errorRes)
            input.requestFocus()
            return null
        }
        input.error = null
        return value
    }

    private fun textValue(inputId: Int): String {
        return findViewById<EditText>(inputId).text?.toString().orEmpty().trim()
    }

    private fun bindSelect(fieldId: Int, optionsId: Int): CocoSelectField {
        return CocoSelectField.bind(
            field = findViewById(fieldId),
            options = resources.getStringArray(optionsId).toList(),
        )
    }

    private fun switchValue(switchId: Int): Boolean {
        return findViewById<Switch>(switchId).isChecked
    }

    private fun dnsServersValue(inputId: Int): String {
        return textValue(inputId)
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun loadExistingChainLink() {
        val config = ChainLinkConfigDataMapper.fromContainer(store.load(configId))
        if (config == null) {
            Toast.makeText(this, getString(R.string.config_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setTextValue(R.id.chain_name_input, config.name)
        setTextValue(R.id.sub_url_input, config.subUrl)
        setTextValue(R.id.exit_uri_input, config.exitUri)
        populateSettings(config.settings)
    }

    private fun existingCreatedAtMillis(): Long {
        return ChainLinkConfigDataMapper.fromContainer(store.load(configId))?.createdAtMillis
            ?: System.currentTimeMillis()
    }

    private fun populateSettings(settings: JSONObject) {
        setTextIfPresent(settings, "subUpdateInterval", R.id.update_interval_input)
        setTextIfPresent(settings, "fetchTimeout", R.id.fetch_timeout_input)
        setTextIfPresent(settings, "maxFronts", R.id.max_fronts_input)
        setSwitchIfPresent(settings, "strict", R.id.strict_switch)
        setSelectIfPresent(settings, "observer", observerSelect)
        setTextIfPresent(settings, "probeUrl", R.id.probe_url_input)
        setTextIfPresent(settings, "connectivityUrl", R.id.connectivity_url_input)
        setTextIfPresent(settings, "probeInterval", R.id.probe_interval_input)
        setTextIfPresent(settings, "probeTimeout", R.id.probe_timeout_input)
        setTextIfPresent(settings, "probeSampling", R.id.probe_sampling_input)
        setSelectIfPresent(settings, "probeMethod", probeMethodSelect)
        if (settings.has("balancerStrategy")) {
            balancerStrategySelect.setValue(settings.optString("balancerStrategy"))
        }
        setTextIfPresent(settings, "expected", R.id.expected_input)
        setTextIfPresent(settings, "maxRtt", R.id.max_rtt_input)
        setTextIfPresent(settings, "baseline", R.id.baseline_input)

        loadingOverrideSections = true
        localInboundsOverridden = settings.has("localInbounds")
        routingDnsOverridden = settings.has("routingDns")
        runtimeOverridden = settings.has("runtime")
        populateLocalInbounds(localInboundsSettings(settings))
        populateRoutingDns(routingDnsSettings(settings))
        populateRuntime(runtimeSettings(settings))
        loadingOverrideSections = false
    }

    private fun localInboundsSettings(settings: JSONObject): JSONObject {
        return sectionWithDefaults(
            defaultLocalInbounds(),
            settings.optJSONObject("localInbounds") ?: globalSettings.optJSONObject("localInbounds")
        )
    }

    private fun routingDnsSettings(settings: JSONObject): JSONObject {
        return sectionWithDefaults(
            defaultRoutingDns(),
            settings.optJSONObject("routingDns") ?: globalSettings.optJSONObject("routingDns")
        )
    }

    private fun runtimeSettings(settings: JSONObject): JSONObject {
        return sectionWithDefaults(
            defaultRuntime(),
            settings.optJSONObject("runtime") ?: globalSettings.optJSONObject("runtime")
        )
    }

    private fun sectionWithDefaults(defaults: JSONObject, values: JSONObject?): JSONObject {
        val merged = JSONObject(defaults.toString())
        values?.keys()?.forEach { key ->
            merged.put(key, values.opt(key))
        }
        return merged
    }

    private fun populateLocalInbounds(settings: JSONObject) {
        setTextValue(R.id.listen_input, settings.optString("listen"))
        inboundsSelect.setValue(settings.optString("inbounds"))
        setTextValue(R.id.socks_port_input, settings.optString("socksPort"))
        setTextValue(R.id.http_port_input, settings.optString("httpPort"))
        setTextValue(R.id.internal_port_input, settings.optString("internalPort"))
    }

    private fun populateRoutingDns(settings: JSONObject) {
        domainStrategySelect.setValue(settings.optString("domainStrategy"))
        setTextValue(R.id.dns_servers_input, settings.optString("dnsServers"))
        setTextValue(R.id.domestic_dns_servers_input, settings.optString("domesticDnsServers"))
        findViewById<Switch>(R.id.use_ipv4_switch).isChecked = settings.optBoolean("useIpv4")
        findViewById<Switch>(R.id.bypass_private_switch).isChecked = settings.optBoolean("bypassPrivate")
        findViewById<Switch>(R.id.block_udp443_switch).isChecked = settings.optBoolean("blockUdp443")
        findViewById<Switch>(R.id.sniff_route_only_switch).isChecked = settings.optBoolean("sniffRouteOnly")
    }

    private fun populateRuntime(settings: JSONObject) {
        setTextValue(R.id.xray_bin_input, settings.optString("xrayBin"))
        loglevelSelect.setValue(settings.optString("loglevel"))
        findViewById<Switch>(R.id.no_run_switch).isChecked = settings.optBoolean("noRun")
        findViewById<Switch>(R.id.once_switch).isChecked = settings.optBoolean("once")
        findViewById<Switch>(R.id.print_config_switch).isChecked = settings.optBoolean("printConfig")
    }

    private fun defaultLocalInbounds(): JSONObject {
        return JSONObject()
            .put("listen", "127.0.0.1")
            .put("inbounds", "socks")
            .put("socksPort", "10809")
            .put("httpPort", "10809")
            .put("internalPort", "10990")
    }

    private fun defaultRoutingDns(): JSONObject {
        return JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("dnsServers", "1.1.1.1 8.8.8.8")
            .put("domesticDnsServers", "")
            .put("useIpv4", true)
            .put("bypassPrivate", true)
            .put("blockUdp443", false)
            .put("sniffRouteOnly", true)
    }

    private fun defaultRuntime(): JSONObject {
        return JSONObject()
            .put("xrayBin", "AndroidLibXrayLite")
            .put("loglevel", "warning")
            .put("noRun", false)
            .put("once", false)
            .put("printConfig", false)
    }

    private fun setTextIfPresent(settings: JSONObject, key: String, inputId: Int) {
        if (settings.has(key)) {
            setTextValue(inputId, settings.optString(key))
        }
    }

    private fun setTextValue(inputId: Int, value: String) {
        findViewById<EditText>(inputId).setText(value)
    }

    private fun setSwitchIfPresent(settings: JSONObject, key: String, switchId: Int) {
        if (settings.has(key)) {
            findViewById<Switch>(switchId).isChecked = settings.optBoolean(key)
        }
    }

    private fun setSelectIfPresent(settings: JSONObject, key: String, select: CocoSelectField) {
        if (!settings.has(key)) {
            return
        }
        select.setValue(settings.optString(key))
    }

    private fun bindClearOverrideButtons() {
        clearLocalInboundsButton.setOnClickListener { confirmClearOverride(OverrideSection.LOCAL_INBOUNDS) }
        clearRoutingDnsButton.setOnClickListener { confirmClearOverride(OverrideSection.ROUTING_DNS) }
        clearRuntimeButton.setOnClickListener { confirmClearOverride(OverrideSection.RUNTIME) }
    }

    private fun confirmClearOverride(section: OverrideSection) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_clear_section_override)
            .setPositiveButton(R.string.action_yes) { _, _ -> clearOverride(section) }
            .setNegativeButton(R.string.action_no, null)
            .show()
    }

    private fun clearOverride(section: OverrideSection) {
        loadingOverrideSections = true
        when (section) {
            OverrideSection.LOCAL_INBOUNDS -> {
                localInboundsOverridden = false
                populateLocalInbounds(localInboundsSettings(JSONObject()))
            }
            OverrideSection.ROUTING_DNS -> {
                routingDnsOverridden = false
                populateRoutingDns(routingDnsSettings(JSONObject()))
            }
            OverrideSection.RUNTIME -> {
                runtimeOverridden = false
                populateRuntime(runtimeSettings(JSONObject()))
            }
        }
        loadingOverrideSections = false
        renderOverrideStatuses()
    }

    private fun bindOverrideChangeTracking() {
        trackTextChanges(OverrideSection.LOCAL_INBOUNDS, R.id.listen_input, R.id.socks_port_input, R.id.http_port_input, R.id.internal_port_input)
        trackSelectChanges(OverrideSection.LOCAL_INBOUNDS, inboundsSelect)

        trackSelectChanges(OverrideSection.ROUTING_DNS, domainStrategySelect)
        trackTextChanges(OverrideSection.ROUTING_DNS, R.id.dns_servers_input, R.id.domestic_dns_servers_input)
        trackSwitchChanges(
            OverrideSection.ROUTING_DNS,
            R.id.use_ipv4_switch,
            R.id.bypass_private_switch,
            R.id.block_udp443_switch,
            R.id.sniff_route_only_switch
        )

        trackTextChanges(OverrideSection.RUNTIME, R.id.xray_bin_input)
        trackSelectChanges(OverrideSection.RUNTIME, loglevelSelect)
        trackSwitchChanges(OverrideSection.RUNTIME, R.id.no_run_switch, R.id.once_switch, R.id.print_config_switch)
    }

    private fun trackTextChanges(section: OverrideSection, vararg inputIds: Int) {
        inputIds.forEach { inputId ->
            findViewById<EditText>(inputId).addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    markSectionOverridden(section)
                }
            })
        }
    }

    private fun trackSelectChanges(section: OverrideSection, vararg selects: CocoSelectField) {
        selects.forEach { select ->
            select.onChanged = {
                markSectionOverridden(section)
            }
        }
    }

    private fun trackSwitchChanges(section: OverrideSection, vararg switchIds: Int) {
        switchIds.forEach { switchId ->
            findViewById<Switch>(switchId).setOnCheckedChangeListener { _, _ ->
                markSectionOverridden(section)
            }
        }
    }

    private fun markSectionOverridden(section: OverrideSection) {
        if (loadingOverrideSections) {
            return
        }

        when (section) {
            OverrideSection.LOCAL_INBOUNDS -> localInboundsOverridden = true
            OverrideSection.ROUTING_DNS -> routingDnsOverridden = true
            OverrideSection.RUNTIME -> runtimeOverridden = true
        }
        renderOverrideStatuses()
    }

    private fun renderOverrideStatuses() {
        renderOverrideStatus(localInboundsStatus, clearLocalInboundsButton, localInboundsOverridden)
        renderOverrideStatus(routingDnsStatus, clearRoutingDnsButton, routingDnsOverridden)
        renderOverrideStatus(runtimeStatus, clearRuntimeButton, runtimeOverridden)
    }

    private fun renderOverrideSectionsVisibility() {
        val visibility = if (overrideSectionsExpanded) View.VISIBLE else View.GONE
        localInboundsSection.visibility = visibility
        routingDnsSection.visibility = visibility
        runtimeSection.visibility = visibility
        globalOverridesButton.isSelected = overrideSectionsExpanded
        globalOverridesButton.alpha = if (overrideSectionsExpanded) 1.0f else 0.82f
    }

    private fun renderOverrideStatus(label: TextView, clearButton: View, overridden: Boolean) {
        label.text = getString(
            if (overridden) R.string.override_status_overriding
            else R.string.override_status_global
        )
        label.setTextColor(this.getColorCompat(if (overridden) R.color.coco_primary else R.color.coco_muted))
        clearButton.visibility = if (overridden) View.VISIBLE else View.GONE
    }

    private fun endpointFromExitUri(exitUri: String): String {
        return try {
            val parsed = Uri.parse(exitUri)
            val host = parsed.host.orEmpty()
            val port = parsed.port
            if (host.isBlank()) {
                getString(R.string.chain_link_type)
            } else if (port > 0) {
                "${maskHost(host)} : $port"
            } else {
                maskHost(host)
            }
        } catch (_: Exception) {
            getString(R.string.chain_link_type)
        }
    }

    private fun maskHost(host: String): String {
        val parts = host.split(".")
        return if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            "${parts[0]}.${parts[1]}.${parts[2]}.***"
        } else {
            host
        }
    }

    private fun renderTitle() {
        findViewById<TextView>(R.id.title_text).text = getString(
            if (isEditMode()) R.string.screen_edit_chain_link_title
            else R.string.screen_add_chain_link_title
        )
    }

    private fun isEditMode(): Boolean = configId != NO_CONFIG_ID

    private enum class OverrideSection {
        LOCAL_INBOUNDS,
        ROUTING_DNS,
        RUNTIME,
    }

    companion object {
        const val EXTRA_CONFIG_ID = "com.player.coco.EXTRA_CONFIG_ID"
        private const val NO_CONFIG_ID = -1L
    }
}
