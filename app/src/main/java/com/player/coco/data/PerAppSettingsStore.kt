package com.player.coco.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PerAppSettings(
    val enabled: Boolean,
    val bypassMode: Boolean,
    val showSystemApps: Boolean,
    val selectedPackages: Set<String>,
)

class PerAppSettingsStore(filesDir: File) {
    private val store = GlobalSettingsStore(filesDir)

    fun load(): PerAppSettings {
        val perApp = store.load().optJSONObject(KEY_PER_APP) ?: JSONObject()
        return PerAppSettings(
            enabled = perApp.optBoolean(KEY_ENABLED, false),
            bypassMode = perApp.optBoolean(KEY_BYPASS_MODE, false),
            showSystemApps = perApp.optBoolean(KEY_SHOW_SYSTEM_APPS, false),
            selectedPackages = perApp.optJSONArray(KEY_SELECTED_PACKAGES).toStringSet(),
        )
    }

    fun save(settings: PerAppSettings) {
        val global = store.load()
        global.put(
            KEY_PER_APP,
            JSONObject()
                .put(KEY_ENABLED, settings.enabled)
                .put(KEY_BYPASS_MODE, settings.bypassMode)
                .put(KEY_SHOW_SYSTEM_APPS, settings.showSystemApps)
                .put(KEY_SELECTED_PACKAGES, JSONArray(settings.selectedPackages.sorted()))
        )
        store.save(global)
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) {
            return emptySet()
        }

        val result = linkedSetOf<String>()
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let { result.add(it) }
        }
        return result
    }

    companion object {
        const val KEY_PER_APP = "perApp"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BYPASS_MODE = "bypassMode"
        private const val KEY_SHOW_SYSTEM_APPS = "showSystemApps"
        private const val KEY_SELECTED_PACKAGES = "selectedPackages"
    }
}
