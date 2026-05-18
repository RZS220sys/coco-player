package com.player.coco.ui.connect.routing

import com.player.coco.R
import com.player.coco.data.routing.RoutingDefaults
import com.player.coco.data.routing.RoutingProfile
import com.player.coco.data.routing.RoutingSettings
import com.player.coco.data.routing.RoutingSettingsJson
import com.player.coco.data.routing.RoutingSettingsStore
import com.player.coco.ui.connect.finishIfConnectDataLocked
import com.player.coco.ui.dp
import com.player.coco.ui.showAnchoredTo

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
import android.widget.TextView
import android.widget.Toast

class RoutingProfilesActivity : Activity() {
    private lateinit var store: RoutingSettingsStore
    private lateinit var summaryText: TextView
    private lateinit var profilesList: ListView
    private lateinit var emptyText: TextView
    private lateinit var adapter: RoutingProfilesAdapter
    private var settings = RoutingSettings(
        version = RoutingSettingsJson.CURRENT_VERSION,
        activeProfileId = 0L,
        profiles = emptyList(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishIfConnectDataLocked()) {
            return
        }
        setContentView(R.layout.activity_routing_profiles)

        store = RoutingSettingsStore(filesDir)
        summaryText = findViewById(R.id.routing_profiles_summary)
        profilesList = findViewById(R.id.routing_profiles_list)
        emptyText = findViewById(R.id.empty_routing_profiles_text)
        adapter = RoutingProfilesAdapter()
        profilesList.adapter = adapter

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.add_profile_button).setOnClickListener { showAddProfileDialog() }
    }

    override fun onStart() {
        super.onStart()
        loadProfiles()
    }

    private fun loadProfiles() {
        settings = store.loadOrCreate()
        renderProfiles()
    }

    private fun renderProfiles() {
        val profiles = settings.profiles.sortedBy { it.sort }
        adapter.submit(profiles)
        profilesList.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
        emptyText.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE

        val activeProfile = profiles.firstOrNull { it.id == settings.activeProfileId }
        summaryText.text = if (activeProfile == null) {
            getString(R.string.routing_profiles_summary_empty)
        } else {
            getString(
                R.string.routing_profiles_summary,
                profiles.size,
                activeProfile.name,
            )
        }
    }

    private fun showAddProfileDialog() {
        val input = profileNameInput("")
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.routing_profile_add)
            .setView(input)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_no, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.routing_profile_name_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addProfile(name)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addProfile(name: String) {
        val firstRuleId = nextRuleId()
        val nextProfile = RoutingProfile(
            id = nextProfileId(),
            name = name,
            enabled = true,
            locked = false,
            sort = nextProfileSort(),
            domainStrategy = RoutingSettingsJson.DEFAULT_DOMAIN_STRATEGY,
            domainMatcher = "",
            sourceUrl = "",
            customIcon = "",
            rules = RoutingDefaults.defaultUserRules(firstRuleId),
        )
        saveSettings(
            settings.copy(
                activeProfileId = settings.activeProfileId.takeIf { it > 0L } ?: nextProfile.id,
                profiles = settings.profiles + nextProfile,
            )
        )
        openProfile(nextProfile.id)
        Toast.makeText(this, R.string.routing_profile_added, Toast.LENGTH_SHORT).show()
    }

    private fun setActiveProfile(profile: RoutingProfile) {
        if (settings.activeProfileId == profile.id) {
            return
        }

        saveSettings(settings.copy(activeProfileId = profile.id))
        Toast.makeText(this, R.string.routing_profile_set_active_done, Toast.LENGTH_SHORT).show()
    }

    private fun duplicateProfile(profile: RoutingProfile) {
        val profileId = nextProfileId()
        var ruleId = nextRuleId()
        val duplicate = profile.copy(
            id = profileId,
            name = getString(R.string.routing_profile_copy_name, profile.name),
            locked = false,
            sort = nextProfileSort(),
            rules = profile.rules
                .sortedBy { it.sort }
                .map { rule -> rule.copy(id = ruleId++) },
        )

        saveSettings(
            settings.copy(
                profiles = settings.profiles + duplicate,
            )
        )
        openProfile(duplicate.id)
        Toast.makeText(this, R.string.routing_profile_duplicated, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteProfile(profile: RoutingProfile) {
        if (profile.locked) {
            Toast.makeText(this, R.string.routing_profile_locked, Toast.LENGTH_SHORT).show()
            return
        }
        if (settings.profiles.size <= 1) {
            Toast.makeText(this, R.string.routing_profile_delete_last, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.routing_profile_delete)
            .setMessage(getString(R.string.routing_profile_delete_confirm, profile.name))
            .setPositiveButton(R.string.action_delete) { _, _ -> deleteProfile(profile) }
            .setNegativeButton(R.string.action_no, null)
            .show()
    }

    private fun deleteProfile(profile: RoutingProfile) {
        val sortedProfiles = settings.profiles.sortedBy { it.sort }
        val removedIndex = sortedProfiles.indexOfFirst { it.id == profile.id }
        val remaining = sortedProfiles.filter { it.id != profile.id }
        if (remaining.isEmpty()) {
            return
        }

        val nextActiveId = if (settings.activeProfileId == profile.id) {
            remaining.getOrNull((removedIndex - 1).coerceAtLeast(0))?.id ?: remaining.first().id
        } else {
            settings.activeProfileId
        }

        saveSettings(
            settings.copy(
                activeProfileId = nextActiveId,
                profiles = remaining,
            )
        )
        Toast.makeText(this, R.string.routing_profile_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings(nextSettings: RoutingSettings) {
        val safeActiveId = nextSettings.activeProfileId.takeIf { activeId ->
            nextSettings.profiles.any { it.id == activeId }
        } ?: nextSettings.profiles.firstOrNull()?.id ?: 0L
        settings = nextSettings.copy(activeProfileId = safeActiveId)
        store.save(settings)
        renderProfiles()
    }

    private fun showProfileMenu(anchor: View, profile: RoutingProfile) {
        val content = LayoutInflater.from(this).inflate(R.layout.popup_routing_profile_menu, null, false)
        val width = dp(248)
        val popup = PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        content.findViewById<View>(R.id.set_active_profile).setOnClickListener {
            popup.dismiss()
            setActiveProfile(profile)
        }
        content.findViewById<View>(R.id.edit_profile).setOnClickListener {
            popup.dismiss()
            openProfile(profile.id)
        }
        content.findViewById<View>(R.id.duplicate_profile).setOnClickListener {
            popup.dismiss()
            duplicateProfile(profile)
        }
        content.findViewById<View>(R.id.delete_profile).setOnClickListener {
            popup.dismiss()
            confirmDeleteProfile(profile)
        }

        popup.showAnchoredTo(anchor, content, width)
    }

    private fun openProfile(profileId: Long) {
        startActivity(
            Intent(this, RoutingProfileDetailActivity::class.java)
                .putExtra(RoutingProfileDetailActivity.EXTRA_PROFILE_ID, profileId)
        )
    }

    private fun profileNameInput(value: String): EditText {
        return EditText(this).apply {
            setText(value)
            selectAll()
            setSingleLine(true)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
    }

    private fun nextProfileId(): Long {
        return (settings.profiles.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    private fun nextProfileSort(): Int {
        return (settings.profiles.maxOfOrNull { it.sort } ?: 0) + PROFILE_SORT_STEP
    }

    private fun nextRuleId(): Long {
        return (settings.profiles.flatMap { it.rules }.maxOfOrNull { it.id } ?: 1000L) + 1L
    }

    private fun profileSummary(profile: RoutingProfile): String {
        val enabledRules = profile.rules.count { it.enabled }
        return getString(
            R.string.routing_profile_row_summary,
            profile.rules.size,
            enabledRules,
            profile.domainStrategy,
        )
    }

    private inner class RoutingProfilesAdapter : BaseAdapter() {
        private var profiles = emptyList<RoutingProfile>()

        fun submit(nextProfiles: List<RoutingProfile>) {
            profiles = nextProfiles
            notifyDataSetChanged()
        }

        override fun getCount(): Int = profiles.size

        override fun getItem(position: Int): RoutingProfile = profiles[position]

        override fun getItemId(position: Int): Long = getItem(position).id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: LayoutInflater.from(this@RoutingProfilesActivity)
                .inflate(R.layout.item_routing_profile, parent, false)
            val profile = getItem(position)
            val isActive = profile.id == settings.activeProfileId

            row.findViewById<TextView>(R.id.routing_profile_name).text = profile.name
            row.findViewById<TextView>(R.id.routing_profile_summary).text = profileSummary(profile)
            row.findViewById<TextView>(R.id.routing_profile_active).visibility =
                if (isActive) View.VISIBLE else View.GONE
            row.setOnClickListener { openProfile(profile.id) }
            row.findViewById<ImageButton>(R.id.routing_profile_more_button).setOnClickListener { anchor ->
                showProfileMenu(anchor, profile)
            }
            return row
        }
    }

    companion object {
        private const val PROFILE_SORT_STEP = 10
    }
}
