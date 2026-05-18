package com.player.coco.ui.connect.routing

import com.player.coco.R
import com.player.coco.data.routing.RoutingProfile
import com.player.coco.data.routing.RoutingRule
import com.player.coco.data.routing.RoutingRuleOutbounds
import com.player.coco.data.routing.RoutingSettings
import com.player.coco.data.routing.RoutingSettingsJson
import com.player.coco.data.routing.RoutingSettingsStore
import com.player.coco.ui.connect.finishIfConnectDataLocked
import com.player.coco.ui.dp
import com.player.coco.ui.showAnchoredTo
import com.player.coco.ui.widget.CocoSelectField

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class RoutingProfileDetailActivity : Activity() {
    private lateinit var store: RoutingSettingsStore
    private lateinit var profileNameInput: EditText
    private lateinit var profileEnabledSwitch: Switch
    private lateinit var domainStrategySelect: CocoSelectField
    private lateinit var sourceUrlInput: EditText
    private lateinit var rulesSummary: TextView
    private lateinit var rulesList: ListView
    private lateinit var emptyRulesText: TextView
    private lateinit var adapter: RoutingRulesAdapter
    private var settings = RoutingSettings(
        version = RoutingSettingsJson.CURRENT_VERSION,
        activeProfileId = 0L,
        profiles = emptyList(),
    )
    private var profileId = 0L
    private var profile: RoutingProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishIfConnectDataLocked()) {
            return
        }
        setContentView(R.layout.activity_routing_profile_detail)

        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
        store = RoutingSettingsStore(filesDir)
        profileNameInput = findViewById(R.id.profile_name_input)
        profileEnabledSwitch = findViewById(R.id.profile_enabled_switch)
        domainStrategySelect = CocoSelectField.bind(
            field = findViewById(R.id.domain_strategy_select),
            options = resources.getStringArray(R.array.domain_strategy_options).toList(),
        )
        sourceUrlInput = findViewById(R.id.source_url_input)
        rulesSummary = findViewById(R.id.rules_summary)
        rulesList = findViewById(R.id.routing_rules_list)
        emptyRulesText = findViewById(R.id.empty_rules_text)
        adapter = RoutingRulesAdapter()
        rulesList.adapter = adapter

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.save_button).setOnClickListener { saveProfile() }
        findViewById<ImageButton>(R.id.add_rule_button).setOnClickListener { addRule() }
    }

    override fun onStart() {
        super.onStart()
        loadProfile()
    }

    private fun loadProfile() {
        settings = store.loadOrCreate()
        val loadedProfile = settings.profiles.firstOrNull { it.id == profileId }
        if (loadedProfile == null) {
            Toast.makeText(this, R.string.routing_profile_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        profile = loadedProfile
        profileNameInput.setText(loadedProfile.name)
        profileEnabledSwitch.isChecked = loadedProfile.enabled
        domainStrategySelect.setValue(loadedProfile.domainStrategy)
        sourceUrlInput.setText(loadedProfile.sourceUrl)
        renderRules()
    }

    private fun renderRules() {
        val rules = currentRules()
        adapter.submit(rules)
        rulesList.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
        emptyRulesText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        rulesSummary.text = if (rules.isEmpty()) {
            getString(R.string.routing_rules_summary_empty)
        } else {
            getString(
                R.string.routing_rules_summary,
                rules.size,
                rules.count { it.enabled },
            )
        }
    }

    private fun saveProfile() {
        saveProfileState(showToast = true, finishAfterSave = true)
    }

    private fun saveProfileState(showToast: Boolean, finishAfterSave: Boolean) {
        val current = profile ?: return
        val name = profileNameInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.routing_profile_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        val updatedProfile = current.copy(
            name = name,
            enabled = profileEnabledSwitch.isChecked,
            domainStrategy = domainStrategySelect.value,
            sourceUrl = sourceUrlInput.text?.toString().orEmpty().trim(),
            rules = currentRules(),
        )
        val updatedProfiles = settings.profiles.map { existing ->
            if (existing.id == updatedProfile.id) updatedProfile else existing
        }
        val activeProfileId = settings.activeProfileId.takeIf { activeId ->
            updatedProfiles.any { it.id == activeId }
        } ?: updatedProfiles.firstOrNull()?.id ?: 0L

        settings = settings.copy(activeProfileId = activeProfileId, profiles = updatedProfiles)
        profile = updatedProfile
        store.save(settings)
        if (showToast) {
            Toast.makeText(this, R.string.routing_profile_saved, Toast.LENGTH_SHORT).show()
        }
        if (finishAfterSave) {
            finish()
        }
    }

    private fun addRule() {
        val nextRule = RoutingRule(
            id = nextRuleId(),
            name = getString(R.string.routing_rule_new),
            enabled = true,
            sort = nextRuleSort(),
            outboundTag = RoutingRuleOutbounds.PROXY,
            port = "",
            network = "",
            protocol = emptyList(),
            inboundTag = emptyList(),
            domain = emptyList(),
            ip = emptyList(),
        )
        updateRules(currentRules() + nextRule)
        saveProfileState(showToast = false, finishAfterSave = false)
        openRuleEditor(nextRule.id)
    }

    private fun toggleRule(rule: RoutingRule) {
        updateRules(
            currentRules().map { existing ->
                if (existing.id == rule.id) existing.copy(enabled = !existing.enabled) else existing
            }
        )
    }

    private fun duplicateRule(rule: RoutingRule) {
        val duplicate = rule.copy(
            id = nextRuleId(),
            name = getString(R.string.routing_rule_copy_name, rule.name),
            sort = nextRuleSort(),
        )
        updateRules(currentRules() + duplicate)
    }

    private fun deleteRule(rule: RoutingRule) {
        updateRules(currentRules().filter { it.id != rule.id })
    }

    private fun moveRule(rule: RoutingRule, direction: Int) {
        val rules = currentRules().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        val targetIndex = index + direction
        if (index !in rules.indices || targetIndex !in rules.indices) {
            return
        }

        val moving = rules.removeAt(index)
        rules.add(targetIndex, moving)
        updateRules(rules)
    }

    private fun updateRules(rules: List<RoutingRule>) {
        val current = profile ?: return
        profile = current.copy(rules = rules.reindexRules())
        renderRules()
    }

    private fun showRenameRuleDialog(rule: RoutingRule) {
        val input = EditText(this).apply {
            setText(rule.name)
            selectAll()
            setSingleLine(true)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.routing_rule_rename)
            .setView(input)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_no, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.routing_rule_name_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                updateRules(
                    currentRules().map { existing ->
                        if (existing.id == rule.id) existing.copy(name = name) else existing
                    }
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showRuleMenu(anchor: View, rule: RoutingRule) {
        val content = LayoutInflater.from(this).inflate(R.layout.popup_routing_rule_menu, null, false)
        val width = dp(248)
        val popup = PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        content.findViewById<View>(R.id.rename_rule).setOnClickListener {
            popup.dismiss()
            showRenameRuleDialog(rule)
        }
        content.findViewById<View>(R.id.move_rule_up).setOnClickListener {
            popup.dismiss()
            moveRule(rule, -1)
        }
        content.findViewById<View>(R.id.move_rule_down).setOnClickListener {
            popup.dismiss()
            moveRule(rule, 1)
        }
        content.findViewById<View>(R.id.duplicate_rule).setOnClickListener {
            popup.dismiss()
            duplicateRule(rule)
        }
        content.findViewById<View>(R.id.delete_rule).setOnClickListener {
            popup.dismiss()
            deleteRule(rule)
        }

        popup.showAnchoredTo(anchor, content, width)
    }

    private fun openRuleEditor(ruleId: Long) {
        saveProfileState(showToast = false, finishAfterSave = false)
        startActivity(
            Intent(this, RoutingRuleEditorActivity::class.java)
                .putExtra(RoutingRuleEditorActivity.EXTRA_PROFILE_ID, profileId)
                .putExtra(RoutingRuleEditorActivity.EXTRA_RULE_ID, ruleId)
        )
    }

    private fun currentRules(): List<RoutingRule> {
        return profile?.rules.orEmpty().sortedBy { it.sort }
    }

    private fun nextRuleId(): Long {
        val savedMax = settings.profiles.flatMap { it.rules }.maxOfOrNull { it.id } ?: 1000L
        val currentMax = currentRules().maxOfOrNull { it.id } ?: 1000L
        return maxOf(savedMax, currentMax) + 1L
    }

    private fun nextRuleSort(): Int {
        return (currentRules().maxOfOrNull { it.sort } ?: 0) + RULE_SORT_STEP
    }

    private fun List<RoutingRule>.reindexRules(): List<RoutingRule> {
        return mapIndexed { index, rule -> rule.copy(sort = (index + 1) * RULE_SORT_STEP) }
    }

    private fun ruleSummary(rule: RoutingRule): String {
        val fields = listOfNotNull(
            rule.port.takeIf { it.isNotBlank() }?.let { getString(R.string.routing_rule_summary_port, it) },
            rule.network.takeIf { it.isNotBlank() }?.let { getString(R.string.routing_rule_summary_network, it) },
            rule.protocol.size.takeIf { it > 0 }?.let { getString(R.string.routing_rule_summary_protocol, it) },
            rule.domain.size.takeIf { it > 0 }?.let { getString(R.string.routing_rule_summary_domain, it) },
            rule.ip.size.takeIf { it > 0 }?.let { getString(R.string.routing_rule_summary_ip, it) },
        )
        return fields.joinToString(" - ").ifBlank { getString(R.string.routing_rule_summary_catch_all) }
    }

    private inner class RoutingRulesAdapter : BaseAdapter() {
        private var rules = emptyList<RoutingRule>()

        fun submit(nextRules: List<RoutingRule>) {
            rules = nextRules
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rules.size

        override fun getItem(position: Int): RoutingRule = rules[position]

        override fun getItemId(position: Int): Long = getItem(position).id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: LayoutInflater.from(this@RoutingProfileDetailActivity)
                .inflate(R.layout.item_routing_rule, parent, false)
            val rule = getItem(position)
            row.findViewById<TextView>(R.id.rule_name).text = rule.name
            row.findViewById<TextView>(R.id.rule_action).text = rule.outboundTag.uppercase()
            row.findViewById<TextView>(R.id.rule_summary).text = ruleSummary(rule)
            val enabledSwitch = row.findViewById<Switch>(R.id.rule_enabled_switch)
            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = rule.enabled
            enabledSwitch.setOnCheckedChangeListener { _, _ -> toggleRule(rule) }
            row.setOnClickListener { openRuleEditor(rule.id) }
            row.findViewById<ImageButton>(R.id.rule_more_button).setOnClickListener { anchor ->
                showRuleMenu(anchor, rule)
            }
            return row
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.player.coco.extra.ROUTING_PROFILE_ID"
        private const val RULE_SORT_STEP = 10
    }
}
