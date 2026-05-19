package com.player.coco.data.config

import android.util.AtomicFile
import com.player.coco.data.ConnectCryptoSession
import com.player.coco.data.ConnectDataCipher
import com.player.coco.data.MusicSettingsStore
import org.json.JSONObject
import java.io.File

class ConnectConfigStore(private val filesDir: File) {
    private val configsDir = File(filesDir, CONFIGS_DIR_NAME)

    fun loadAll(): List<ConnectConfigContainer> {
        return configFiles()
            .mapNotNull { readContainer(it)?.toConnectConfigContainer() }
            .sortedBy { it.id }
    }

    fun load(id: Long): ConnectConfigContainer? {
        if (id <= 0L) {
            return null
        }
        return readContainer(File(configsDir, "config-$id.json"))?.toConnectConfigContainer()
    }

    fun saveNew(type: String, data: JSONObject): ConnectConfigContainer {
        configsDir.mkdirs()
        return save(nextId(), type, data)
    }

    fun saveExisting(id: Long, type: String, data: JSONObject): ConnectConfigContainer {
        val dataToSave = load(id)?.data?.let { existing ->
            ConnectConfigMetrics.preserve(existing, data)
        } ?: data
        return save(id, type, dataToSave)
    }

    fun saveDisplayMetric(id: Long, metricKey: String, value: Int): ConnectConfigContainer? {
        val config = load(id) ?: return null
        return save(
            id = config.id,
            type = config.type,
            data = ConnectConfigMetrics.withMetric(config.data, metricKey, value),
        )
    }

    fun delete(id: Long): Boolean {
        if (id <= 0L) {
            return false
        }

        return File(configsDir, "config-$id.json").delete()
    }

    private fun save(id: Long, type: String, data: JSONObject): ConnectConfigContainer {
        val config = ConnectConfigContainer(
            id = id,
            type = type,
            data = data,
        )
        writeContainer(config)
        return config
    }

    private fun configFiles(): List<File> {
        if (!configsDir.exists()) {
            return emptyList()
        }
        return configsDir
            .listFiles { file -> file.isFile && CONFIG_FILE_PATTERN.matches(file.name) }
            .orEmpty()
            .toList()
    }

    private fun readContainer(file: File): JSONObject? {
        return try {
            ConnectDataCipher.readJson(file.readText(Charsets.UTF_8), ConnectCryptoSession.keyOrNull())
        } catch (_: Exception) {
            null
        }
    }

    private fun writeContainer(config: ConnectConfigContainer) {
        configsDir.mkdirs()
        val atomicFile = AtomicFile(File(configsDir, "config-${config.id}.json"))
        val output = atomicFile.startWrite()
        try {
            val container = config.toContainerJson()
            val dataToWrite = if (MusicSettingsStore(filesDir).hasCocoConnectAuth()) {
                val key = ConnectCryptoSession.keyOrNull()
                    ?: throw IllegalStateException("Coco connect data is locked.")
                ConnectDataCipher.serializeJson(container, key)
            } else {
                ConnectDataCipher.serializeJson(container, key = null)
            }
            output.write(dataToWrite.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun nextId(): Long {
        return configFiles()
            .mapNotNull { CONFIG_FILE_PATTERN.matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull() }
            .maxOrNull()
            ?.plus(1L)
            ?: 1L
    }

    private fun JSONObject.toConnectConfigContainer(): ConnectConfigContainer? {
        val id = optLong(KEY_ID, 0L)
        val type = optString(KEY_TYPE)
        val data = optJSONObject(KEY_DATA) ?: return null
        if (id <= 0L || type.isBlank()) {
            return null
        }
        return ConnectConfigContainer(
            id = id,
            type = type,
            data = data,
        )
    }

    private fun ConnectConfigContainer.toContainerJson(): JSONObject {
        return JSONObject()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type)
            .put(KEY_DATA, data)
    }

    companion object {
        const val GENERATED_XRAY_CONFIG_NAME = "xray-config.json"

        private const val CONFIGS_DIR_NAME = "configs"
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_DATA = "data"
        private val CONFIG_FILE_PATTERN = Regex("""config-(\d+)\.json""")
    }
}
