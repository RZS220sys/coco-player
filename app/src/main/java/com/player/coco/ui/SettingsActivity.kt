package com.player.coco.ui

import com.player.coco.data.GlobalSettingsStore
import com.player.coco.data.PerAppSettingsStore
import com.player.coco.R
import com.player.coco.ui.widget.CocoSelectField

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class SettingsActivity : Activity() {
    private lateinit var store: GlobalSettingsStore
    private lateinit var appModeSelect: CocoSelectField
    private lateinit var inboundsSelect: CocoSelectField
    private lateinit var domainStrategySelect: CocoSelectField
    private lateinit var loglevelSelect: CocoSelectField
    private lateinit var xrayLoglevelSelect: CocoSelectField

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = GlobalSettingsStore(filesDir)
        appModeSelect = bindSelect(R.id.app_mode_select, R.array.app_mode_options)
        inboundsSelect = bindSelect(R.id.inbounds_select, R.array.inbound_type_options)
        domainStrategySelect = bindSelect(R.id.domain_strategy_select, R.array.domain_strategy_options)
        loglevelSelect = bindSelect(R.id.loglevel_select, R.array.loglevel_options)
        xrayLoglevelSelect = bindSelect(R.id.xray_loglevel_select, R.array.loglevel_options)

        findViewById<View>(R.id.back_button).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.save_button).setOnClickListener {
            saveSettings()
        }

        populateSettings(store.load())
    }

    private fun saveSettings() {
        store.save(buildSettingsJson())
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun buildSettingsJson(): JSONObject {
        val existing = store.load()
        return JSONObject()
            .put("appMode", appModeSelect.value)
            .put(
                "localInbounds",
                JSONObject()
                    .put("listen", textValue(R.id.listen_input))
                    .put("inbounds", inboundsSelect.value)
                    .put("socksPort", textValue(R.id.socks_port_input))
                    .put("httpPort", textValue(R.id.http_port_input))
                    .put("internalPort", textValue(R.id.internal_port_input))
            )
            .put(
                "routingDns",
                JSONObject()
                    .put("domainStrategy", domainStrategySelect.value)
                    .put("dnsServers", dnsServersValue(R.id.dns_servers_input))
                    .put("domesticDnsServers", dnsServersValue(R.id.domestic_dns_servers_input))
                    .put("useIpv4", switchValue(R.id.use_ipv4_switch))
                    .put("bypassPrivate", switchValue(R.id.bypass_private_switch))
                    .put("blockUdp443", switchValue(R.id.block_udp443_switch))
                    .put("sniffRouteOnly", switchValue(R.id.sniff_route_only_switch))
            )
            .put(
                "runtime",
                JSONObject()
                    .put("xrayBin", textValue(R.id.xray_bin_input))
                    .put("loglevel", loglevelSelect.value)
                    .put("noRun", switchValue(R.id.no_run_switch))
                    .put("once", switchValue(R.id.once_switch))
                    .put("printConfig", switchValue(R.id.print_config_switch))
            )
            .put(
                "logs",
                JSONObject()
                    .put("xrayLogLevel", xrayLoglevelSelect.value)
                    .put("accessLogsEnabled", switchValue(R.id.xray_access_logs_switch))
                    .put("suppressNoisyLogs", switchValue(R.id.suppress_noisy_logs_switch))
                    .put("autoTrimMb", textValue(R.id.auto_trim_logs_mb_input).ifBlank { "5" })
            )
            .also { settings ->
                existing.optJSONObject(PerAppSettingsStore.KEY_PER_APP)?.let {
                    settings.put(PerAppSettingsStore.KEY_PER_APP, it)
                }
            }
    }

    private fun populateSettings(settings: JSONObject) {
        setSelectIfPresent(settings, "appMode", appModeSelect)

        val localInbounds = settings.optJSONObject("localInbounds") ?: JSONObject()
        setTextIfPresent(localInbounds, "listen", R.id.listen_input)
        setSelectIfPresent(localInbounds, "inbounds", inboundsSelect)
        setTextIfPresent(localInbounds, "socksPort", R.id.socks_port_input)
        setTextIfPresent(localInbounds, "httpPort", R.id.http_port_input)
        setTextIfPresent(localInbounds, "internalPort", R.id.internal_port_input)

        val routingDns = settings.optJSONObject("routingDns") ?: JSONObject()
        setSelectIfPresent(routingDns, "domainStrategy", domainStrategySelect)
        setTextIfPresent(routingDns, "dnsServers", R.id.dns_servers_input)
        setTextIfPresent(routingDns, "domesticDnsServers", R.id.domestic_dns_servers_input)
        setSwitchIfPresent(routingDns, "useIpv4", R.id.use_ipv4_switch)
        setSwitchIfPresent(routingDns, "bypassPrivate", R.id.bypass_private_switch)
        setSwitchIfPresent(routingDns, "blockUdp443", R.id.block_udp443_switch)
        setSwitchIfPresent(routingDns, "sniffRouteOnly", R.id.sniff_route_only_switch)

        val runtime = settings.optJSONObject("runtime") ?: JSONObject()
        setTextIfPresent(runtime, "xrayBin", R.id.xray_bin_input)
        setSelectIfPresent(runtime, "loglevel", loglevelSelect)
        setSwitchIfPresent(runtime, "noRun", R.id.no_run_switch)
        setSwitchIfPresent(runtime, "once", R.id.once_switch)
        setSwitchIfPresent(runtime, "printConfig", R.id.print_config_switch)

        val logs = settings.optJSONObject("logs") ?: JSONObject()
        setSelectIfPresent(logs, "xrayLogLevel", xrayLoglevelSelect)
        setSwitchIfPresent(logs, "accessLogsEnabled", R.id.xray_access_logs_switch)
        findViewById<Switch>(R.id.suppress_noisy_logs_switch).isChecked =
            logs.optBoolean("suppressNoisyLogs", true)
        setTextIfPresent(logs, "autoTrimMb", R.id.auto_trim_logs_mb_input)
    }

    private fun textValue(inputId: Int): String {
        return findViewById<EditText>(inputId).text?.toString().orEmpty().trim()
    }

    private fun dnsServersValue(inputId: Int): String {
        return textValue(inputId)
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
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

    private fun setTextIfPresent(settings: JSONObject, key: String, inputId: Int) {
        if (settings.has(key)) {
            findViewById<EditText>(inputId).setText(settings.optString(key))
        }
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
}
