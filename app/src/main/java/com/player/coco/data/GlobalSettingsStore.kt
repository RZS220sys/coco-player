package com.player.coco.data

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

class GlobalSettingsStore(private val filesDir: File) {
    private val settingsFile = File(filesDir, SETTINGS_FILE_NAME)

    fun load(): JSONObject {
        if (!settingsFile.exists()) {
            return JSONObject()
        }

        return try {
            JSONObject(settingsFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun save(settings: JSONObject) {
        filesDir.mkdirs()
        val atomicFile = AtomicFile(settingsFile)
        val output = atomicFile.startWrite()
        try {
            output.write(settings.toString(2).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun appMode(): String {
        return load().optString(KEY_APP_MODE, APP_MODE_VPN)
    }

    fun activeConfigId(): Long {
        return load().optLong(KEY_ACTIVE_CONFIG_ID, 0L)
    }

    fun saveActiveConfigId(configId: Long) {
        val settings = load()
        settings.put(KEY_ACTIVE_CONFIG_ID, configId)
        save(settings)
    }

    companion object {
        const val APP_MODE_VPN = "VPN"
        const val APP_MODE_PROXY_ONLY = "Proxy-only"

        private const val SETTINGS_FILE_NAME = "global_settings.json"
        private const val KEY_APP_MODE = "appMode"
        private const val KEY_ACTIVE_CONFIG_ID = "activeConfigId"
    }
}
