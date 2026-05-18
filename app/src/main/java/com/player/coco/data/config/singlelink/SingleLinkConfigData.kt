package com.player.coco.data.config.singlelink

import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import org.json.JSONObject

data class SingleLinkConfigData(
    val name: String,
    val protocol: String,
    val values: JSONObject,
    val endpoint: String,
    val createdAtMillis: Long,
    val settings: JSONObject,
)

data class SingleLinkDraft(
    val name: String,
    val protocol: String,
    val values: JSONObject,
    val endpoint: String,
    val settings: JSONObject,
)

object SingleLinkConfigDataMapper {
    fun fromContainer(container: ConnectConfigContainer?): SingleLinkConfigData? {
        if (container?.type != ConnectConfigTypes.SINGLE_LINK) {
            return null
        }
        return fromJson(container.data)
    }

    fun fromJson(data: JSONObject): SingleLinkConfigData {
        return SingleLinkConfigData(
            name = data.optString(KEY_NAME),
            protocol = data.optString(KEY_PROTOCOL),
            values = data.optJSONObject(KEY_VALUES) ?: JSONObject(),
            endpoint = data.optString(KEY_ENDPOINT),
            createdAtMillis = data.optLong(KEY_CREATED_AT),
            settings = data.optJSONObject(KEY_SETTINGS) ?: JSONObject(),
        )
    }

    fun toJson(draft: SingleLinkDraft, createdAtMillis: Long = System.currentTimeMillis()): JSONObject {
        return JSONObject()
            .put(KEY_NAME, draft.name)
            .put(KEY_PROTOCOL, draft.protocol)
            .put(KEY_VALUES, draft.values)
            .put(KEY_ENDPOINT, draft.endpoint)
            .put(KEY_CREATED_AT, createdAtMillis)
            .put(KEY_SETTINGS, draft.settings)
    }

    private const val KEY_NAME = "name"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_VALUES = "values"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_CREATED_AT = "createdAtMillis"
    private const val KEY_SETTINGS = "settings"
}
