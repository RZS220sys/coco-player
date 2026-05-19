package com.player.coco.data.config

import org.json.JSONObject

object ConnectConfigMetrics {
    const val KEY_LAST_REAL_DELAY = "lastRealDelay"
    const val KEY_LAST_TCPING = "lastTcping"

    fun lastRealDelay(config: ConnectConfigContainer): Int? {
        return metric(config.data, KEY_LAST_REAL_DELAY)
    }

    fun lastTcping(config: ConnectConfigContainer): Int? {
        return metric(config.data, KEY_LAST_TCPING)
    }

    fun preserve(from: JSONObject, into: JSONObject): JSONObject {
        copyIfMissing(from, into, KEY_LAST_REAL_DELAY)
        copyIfMissing(from, into, KEY_LAST_TCPING)
        return into
    }

    fun withMetric(data: JSONObject, key: String, value: Int): JSONObject {
        return JSONObject(data.toString()).put(key, value)
    }

    private fun metric(data: JSONObject, key: String): Int? {
        if (!data.has(key)) {
            return null
        }
        return data.optInt(key)
    }

    private fun copyIfMissing(from: JSONObject, into: JSONObject, key: String) {
        if (!into.has(key) && from.has(key)) {
            into.put(key, from.opt(key))
        }
    }
}
