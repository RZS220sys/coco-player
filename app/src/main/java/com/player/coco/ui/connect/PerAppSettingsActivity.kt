package com.player.coco.ui.connect

import com.player.coco.data.PerAppSettings
import com.player.coco.data.PerAppSettingsStore
import com.player.coco.logging.CocoLog
import com.player.coco.R
import com.player.coco.ui.dp
import com.player.coco.ui.showAnchoredTo
import com.player.coco.xray.runtime.XrayConnectionState
import com.player.coco.xray.runtime.XrayServiceActions

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class PerAppSettingsActivity : Activity() {
    private lateinit var store: PerAppSettingsStore
    private lateinit var enabledSwitch: Switch
    private lateinit var bypassSwitch: Switch
    private lateinit var showSystemAppsSwitch: Switch
    private lateinit var modeHelpText: TextView
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: View
    private lateinit var appCountText: TextView
    private lateinit var appsList: ListView
    private lateinit var emptyAppsText: TextView
    private lateinit var adapter: AppsAdapter
    private val allApps = mutableListOf<AppEntry>()
    private val iconCache = mutableMapOf<String, Drawable?>()
    private var selectedPackages = linkedSetOf<String>()
    private var loadingApps = false
    private var loadingSettings = false
    private var initialSettings: PerAppSettings? = null
    private var handledExit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_per_app_settings)

        store = PerAppSettingsStore(filesDir)
        enabledSwitch = findViewById(R.id.per_app_enabled_switch)
        bypassSwitch = findViewById(R.id.per_app_bypass_switch)
        showSystemAppsSwitch = findViewById(R.id.show_system_apps_switch)
        modeHelpText = findViewById(R.id.per_app_mode_help)
        searchInput = findViewById(R.id.search_input)
        clearSearchButton = findViewById(R.id.clear_search_button)
        appCountText = findViewById(R.id.app_count_text)
        appsList = findViewById(R.id.apps_list)
        emptyAppsText = findViewById(R.id.empty_apps_text)
        adapter = AppsAdapter()
        appsList.adapter = adapter

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.more_button).setOnClickListener { anchor -> showOverflow(anchor) }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
            searchInput.requestFocus()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        enabledSwitch.setOnCheckedChangeListener { _, _ ->
            if (!loadingSettings) {
                saveState()
            }
        }
        bypassSwitch.setOnCheckedChangeListener { _, _ ->
            renderModeHelp()
            if (!loadingSettings) {
                saveState()
            }
        }
        showSystemAppsSwitch.setOnCheckedChangeListener { _, _ ->
            renderApps()
            if (!loadingSettings) {
                saveState()
            }
        }

        loadSettings()
        loadInstalledApps()
    }

    override fun finish() {
        handlePerAppExit()
        super.finish()
    }

    private fun loadSettings() {
        loadingSettings = true
        val settings = store.load()
        initialSettings = settings
        selectedPackages = settings.selectedPackages.toCollection(linkedSetOf())
        enabledSwitch.isChecked = settings.enabled
        bypassSwitch.isChecked = settings.bypassMode
        showSystemAppsSwitch.isChecked = settings.showSystemApps
        loadingSettings = false
        renderModeHelp()
    }

    private fun loadInstalledApps() {
        loadingApps = true
        appCountText.text = getString(R.string.per_app_loading)
        Thread {
            val apps = packageManager
                .getInstalledApplications(0)
                .mapNotNull { info -> info.toAppEntryOrNull() }
                .distinctBy { it.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                loadingApps = false
                allApps.clear()
                allApps.addAll(apps)
                renderApps()
            }
        }.start()
    }

    private fun ApplicationInfo.toAppEntryOrNull(): AppEntry? {
        val packageName = packageName.orEmpty()
        if (packageName.isBlank()) {
            return null
        }
        return AppEntry(
            label = loadLabel(packageManager).toString().takeIf { it.isNotBlank() } ?: packageName,
            packageName = packageName,
            isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        )
    }

    private fun renderApps() {
        val query = searchInput.text?.toString().orEmpty().trim().lowercase(Locale.US)
        val showSystemApps = showSystemAppsSwitch.isChecked
        val visible = allApps
            .asSequence()
            .filter { app -> showSystemApps || !app.isSystem || app.packageName in selectedPackages }
            .filter { app ->
                query.isBlank() ||
                    app.label.lowercase(Locale.US).contains(query) ||
                    app.packageName.lowercase(Locale.US).contains(query)
            }
            .sortedWith(appComparator())
            .toList()

        adapter.submit(visible)
        clearSearchButton.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
        emptyAppsText.visibility = if (!loadingApps && visible.isEmpty()) View.VISIBLE else View.GONE
        appsList.visibility = if (visible.isEmpty()) View.GONE else View.VISIBLE
        appCountText.text = if (loadingApps) {
            getString(R.string.per_app_loading)
        } else {
            val availableCount = allApps.count { showSystemApps || !it.isSystem || it.packageName in selectedPackages }
            getString(R.string.per_app_count, selectedPackages.size, visible.size, availableCount)
        }
    }

    private fun renderModeHelp() {
        modeHelpText.text = getString(
            if (bypassSwitch.isChecked) R.string.per_app_bypass_help
            else R.string.per_app_include_help
        )
    }

    private fun togglePackage(packageName: String) {
        if (!selectedPackages.add(packageName)) {
            selectedPackages.remove(packageName)
        }
        renderApps()
        saveState()
    }

    private fun saveState() {
        store.save(currentSettings())
    }

    private fun currentSettings(): PerAppSettings {
        return PerAppSettings(
            enabled = enabledSwitch.isChecked,
            bypassMode = bypassSwitch.isChecked,
            showSystemApps = showSystemAppsSwitch.isChecked,
            selectedPackages = selectedPackages,
        )
    }

    private fun handlePerAppExit() {
        if (handledExit) {
            return
        }
        handledExit = true

        val before = initialSettings ?: return
        if (!requiresVpnRestart(before, currentSettings())) {
            return
        }

        val snapshot = XrayConnectionState.snapshot()
        if (snapshot.state == XrayConnectionState.STATE_CONNECTED &&
            snapshot.mode == XrayServiceActions.MODE_VPN &&
            snapshot.configId > 0L
        ) {
            CocoLog.info(this, TAG, "Per-app settings changed; restarting VPN to apply app rules.")
            XrayConnectionState.publishConnecting(this, XrayServiceActions.MODE_VPN, snapshot.configId)
            XrayServiceActions.restartVpn(this, snapshot.configId)
        }
    }

    private fun requiresVpnRestart(before: PerAppSettings, after: PerAppSettings): Boolean {
        return before.enabled != after.enabled ||
            before.bypassMode != after.bypassMode ||
            before.selectedPackages != after.selectedPackages
    }

    private fun showOverflow(anchor: View) {
        val content = LayoutInflater.from(this).inflate(R.layout.popup_per_app_menu, null, false)
        val width = dp(288)
        val popup = PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        content.findViewById<View>(R.id.select_all_apps).setOnClickListener {
            adapter.visiblePackages().forEach { selectedPackages.add(it) }
            finishMenuAction(popup)
        }
        content.findViewById<View>(R.id.invert_app_selection).setOnClickListener {
            adapter.visiblePackages().forEach { packageName ->
                if (!selectedPackages.add(packageName)) {
                    selectedPackages.remove(packageName)
                }
            }
            finishMenuAction(popup)
        }
        content.findViewById<View>(R.id.auto_select_proxy_app).setOnClickListener {
            selectedPackages.add(packageName)
            finishMenuAction(popup)
        }
        content.findViewById<View>(R.id.import_apps_clipboard).setOnClickListener {
            importFromClipboard()
            finishMenuAction(popup)
        }
        content.findViewById<View>(R.id.export_apps_clipboard).setOnClickListener {
            exportToClipboard()
            popup.dismiss()
        }

        popup.showAnchoredTo(anchor, content, width)
    }

    private fun finishMenuAction(popup: PopupWindow) {
        popup.dismiss()
        renderApps()
        saveState()
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val knownPackages = allApps.map { it.packageName }.toSet()
        val imported = text
            .split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it in knownPackages }
        selectedPackages.addAll(imported)
        Toast.makeText(this, getString(R.string.per_app_imported_count, imported.size), Toast.LENGTH_SHORT).show()
    }

    private fun exportToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = selectedPackages.sorted().joinToString(separator = "\n")
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label_per_app_packages), text))
        Toast.makeText(this, R.string.per_app_exported, Toast.LENGTH_SHORT).show()
    }

    private fun loadIcon(packageName: String): Drawable? {
        if (!iconCache.containsKey(packageName)) {
            iconCache[packageName] = runCatching {
                packageManager.getApplicationIcon(packageName)
            }.getOrNull()
        }
        return iconCache[packageName]
    }

    private fun appComparator(): Comparator<AppEntry> {
        return compareByDescending<AppEntry> { it.packageName in selectedPackages }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            .thenBy { it.packageName }
    }

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val isSystem: Boolean,
    )

    private inner class AppsAdapter : BaseAdapter() {
        private var apps = emptyList<AppEntry>()

        fun submit(nextApps: List<AppEntry>) {
            apps = nextApps
            notifyDataSetChanged()
        }

        fun visiblePackages(): List<String> = apps.map { it.packageName }

        override fun getCount(): Int = apps.size

        override fun getItem(position: Int): AppEntry = apps[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: LayoutInflater.from(this@PerAppSettingsActivity)
                .inflate(R.layout.item_per_app, parent, false)
            val app = getItem(position)
            row.findViewById<ImageView>(R.id.app_icon).setImageDrawable(loadIcon(app.packageName))
            row.findViewById<TextView>(R.id.app_name).text = if (app.isSystem) {
                getString(R.string.per_app_system_app_name, app.label)
            } else {
                app.label
            }
            row.findViewById<TextView>(R.id.app_package).text = app.packageName
            val selectedCheck = row.findViewById<CheckBox>(R.id.app_selected)
            selectedCheck.isChecked = app.packageName in selectedPackages
            row.setOnClickListener { togglePackage(app.packageName) }
            selectedCheck.setOnClickListener { togglePackage(app.packageName) }
            return row
        }
    }

    companion object {
        private const val TAG = "CocoPerApp"
    }
}
