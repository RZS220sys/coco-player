package com.player.coco.data

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

data class ChainLinkConfig(
    val id: Long,
    val type: String,
    val name: String,
    val subUrl: String,
    val exitUri: String,
    val endpoint: String,
    val createdAtMillis: Long,
    val settings: JSONObject,
)

class ChainLinkStore(private val filesDir: File) {
    private val configsDir = File(filesDir, CONFIGS_DIR_NAME)

    fun loadAll(): List<ChainLinkConfig> {
        return configFiles()
            .mapNotNull { readContainer(it)?.toChainLinkConfig() }
            .sortedBy { it.id }
    }

    fun load(id: Long): ChainLinkConfig? {
        if (id <= 0L) {
            return null
        }
        return readContainer(File(configsDir, "config-$id.json"))?.toChainLinkConfig()
    }

    fun saveNew(input: ChainLinkDraft): ChainLinkConfig {
        configsDir.mkdirs()
        val nextId = nextId()
        return save(nextId, input, System.currentTimeMillis())
    }

    fun saveExisting(id: Long, input: ChainLinkDraft): ChainLinkConfig {
        val createdAtMillis = load(id)?.createdAtMillis ?: System.currentTimeMillis()
        return save(id, input, createdAtMillis)
    }

    fun delete(id: Long): Boolean {
        if (id <= 0L) {
            return false
        }

        return File(configsDir, "config-$id.json").delete()
    }

    private fun save(id: Long, input: ChainLinkDraft, createdAtMillis: Long): ChainLinkConfig {
        val config = ChainLinkConfig(
            id = id,
            type = TYPE_CHAIN_LINK,
            name = input.name,
            subUrl = input.subUrl,
            exitUri = input.exitUri,
            endpoint = input.endpoint,
            createdAtMillis = createdAtMillis,
            settings = input.settings,
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
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun writeContainer(config: ChainLinkConfig) {
        configsDir.mkdirs()
        val atomicFile = AtomicFile(File(configsDir, "config-${config.id}.json"))
        val output = atomicFile.startWrite()
        try {
            output.write(config.toContainerJson().toString(2).toByteArray(Charsets.UTF_8))
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

    private fun JSONObject.toChainLinkConfig(): ChainLinkConfig? {
        val type = optString(KEY_TYPE)
        if (type != TYPE_CHAIN_LINK) {
            return null
        }

        val id = optLong(KEY_ID, 0L)
        val data = optJSONObject(KEY_DATA) ?: return null
        if (id <= 0L) {
            return null
        }

        return ChainLinkConfig(
            id = id,
            type = type,
            name = data.optString(KEY_NAME),
            subUrl = data.optString(KEY_SUB_URL),
            exitUri = data.optString(KEY_EXIT_URI),
            endpoint = data.optString(KEY_ENDPOINT),
            createdAtMillis = data.optLong(KEY_CREATED_AT),
            settings = data.optJSONObject(KEY_SETTINGS) ?: JSONObject(),
        )
    }

    private fun ChainLinkConfig.toContainerJson(): JSONObject {
        return JSONObject()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type)
            .put(KEY_DATA, toDataJson())
    }

    private fun ChainLinkConfig.toDataJson(): JSONObject {
        return JSONObject()
            .put(KEY_NAME, name)
            .put(KEY_SUB_URL, subUrl)
            .put(KEY_EXIT_URI, exitUri)
            .put(KEY_ENDPOINT, endpoint)
            .put(KEY_CREATED_AT, createdAtMillis)
            .put(KEY_SETTINGS, settings)
    }

    companion object {
        const val GENERATED_XRAY_CONFIG_NAME = "xray-config.json"
        const val TYPE_CHAIN_LINK = "chain-link"

        private const val CONFIGS_DIR_NAME = "configs"
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_DATA = "data"
        private const val KEY_NAME = "name"
        private const val KEY_SUB_URL = "subUrl"
        private const val KEY_EXIT_URI = "exitUri"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_CREATED_AT = "createdAtMillis"
        private const val KEY_SETTINGS = "settings"
        private val CONFIG_FILE_PATTERN = Regex("""config-(\d+)\.json""")
    }
}

data class ChainLinkDraft(
    val name: String,
    val subUrl: String,
    val exitUri: String,
    val endpoint: String,
    val settings: JSONObject,
)
