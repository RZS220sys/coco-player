package com.player.coco.data.routing

import android.util.AtomicFile
import com.player.coco.data.ConnectCryptoSession
import com.player.coco.data.ConnectDataCipher
import com.player.coco.data.GlobalSettingsStore
import com.player.coco.data.MusicSettingsStore
import org.json.JSONObject
import java.io.File

class RoutingSettingsStore(private val filesDir: File) {
    private val settingsFile = File(filesDir, SETTINGS_FILE_NAME)

    fun load(): RoutingSettings {
        if (!settingsFile.exists()) {
            return defaultSettings()
        }

        return try {
            val json = ConnectDataCipher.readJson(settingsFile.readText(Charsets.UTF_8), ConnectCryptoSession.keyOrNull())
                ?: return defaultSettings()
            RoutingSettingsJson.fromJson(json).takeIf { it.profiles.isNotEmpty() }
                ?: defaultSettings()
        } catch (_: Exception) {
            defaultSettings()
        }
    }

    fun loadOrCreate(): RoutingSettings {
        val settings = load()
        val upgradedSettings = RoutingDefaults.upgradeLegacyDefault(settings)
        if (!settingsFile.exists() || upgradedSettings != settings) {
            save(upgradedSettings)
        }
        return upgradedSettings
    }

    fun save(settings: RoutingSettings) {
        filesDir.mkdirs()
        val atomicFile = AtomicFile(settingsFile)
        val output = atomicFile.startWrite()
        try {
            val dataToWrite = if (MusicSettingsStore(filesDir).hasCocoConnectAuth()) {
                val key = ConnectCryptoSession.keyOrNull()
                    ?: throw IllegalStateException("Coco connect data is locked.")
                ConnectDataCipher.serializeJson(RoutingSettingsJson.toJson(settings), key)
            } else {
                ConnectDataCipher.serializeJson(RoutingSettingsJson.toJson(settings), key = null)
            }
            output.write(dataToWrite.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun defaultSettings(): RoutingSettings {
        val routingDns = GlobalSettingsStore(filesDir).load().optJSONObject(KEY_ROUTING_DNS) ?: JSONObject()
        return RoutingDefaults.fromRoutingDns(routingDns)
    }

    companion object {
        const val SETTINGS_FILE_NAME = "routing_settings.json"
        private const val KEY_ROUTING_DNS = "routingDns"
    }
}
