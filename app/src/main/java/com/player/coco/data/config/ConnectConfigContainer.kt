package com.player.coco.data.config

import org.json.JSONObject

data class ConnectConfigContainer(
    val id: Long,
    val type: String,
    val data: JSONObject,
)
