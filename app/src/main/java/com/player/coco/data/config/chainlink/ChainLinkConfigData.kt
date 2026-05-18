package com.player.coco.data.config.chainlink

import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import org.json.JSONObject

data class ChainLinkConfigData(
    val name: String,
    val subUrl: String,
    val exitUri: String,
    val endpoint: String,
    val createdAtMillis: Long,
    val settings: JSONObject,
)

data class ChainLinkDraft(
    val name: String,
    val subUrl: String,
    val exitUri: String,
    val endpoint: String,
    val settings: JSONObject,
)

object ChainLinkConfigDataMapper {
    fun fromContainer(container: ConnectConfigContainer?): ChainLinkConfigData? {
        if (container?.type != ConnectConfigTypes.CHAIN_LINK) {
            return null
        }
        return fromJson(container.data)
    }

    fun fromJson(data: JSONObject): ChainLinkConfigData {
        return ChainLinkConfigData(
            name = data.optString(KEY_NAME),
            subUrl = data.optString(KEY_SUB_URL),
            exitUri = data.optString(KEY_EXIT_URI),
            endpoint = data.optString(KEY_ENDPOINT),
            createdAtMillis = data.optLong(KEY_CREATED_AT),
            settings = data.optJSONObject(KEY_SETTINGS) ?: JSONObject(),
        )
    }

    fun toJson(draft: ChainLinkDraft, createdAtMillis: Long = System.currentTimeMillis()): JSONObject {
        return JSONObject()
            .put(KEY_NAME, draft.name)
            .put(KEY_SUB_URL, draft.subUrl)
            .put(KEY_EXIT_URI, draft.exitUri)
            .put(KEY_ENDPOINT, draft.endpoint)
            .put(KEY_CREATED_AT, createdAtMillis)
            .put(KEY_SETTINGS, draft.settings)
    }

    private const val KEY_NAME = "name"
    private const val KEY_SUB_URL = "subUrl"
    private const val KEY_EXIT_URI = "exitUri"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_CREATED_AT = "createdAtMillis"
    private const val KEY_SETTINGS = "settings"
}
