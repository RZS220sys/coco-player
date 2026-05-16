package com.player.coco.xray.runtime

import android.content.Context
import android.content.Intent

data class XrayConnectionSnapshot(
    val state: String,
    val mode: String?,
    val configId: Long,
)

object XrayConnectionState {
    const val ACTION_CHANGED = "com.player.coco.action.XRAY_CONNECTION_STATE_CHANGED"
    const val EXTRA_STATE = "com.player.coco.extra.CONNECTION_STATE"
    const val EXTRA_MODE = "com.player.coco.extra.CONNECTION_MODE"
    const val EXTRA_CONFIG_ID = "com.player.coco.extra.CONNECTION_CONFIG_ID"

    const val STATE_DISCONNECTED = "disconnected"
    const val STATE_CONNECTING = "connecting"
    const val STATE_CONNECTED = "connected"

    private var currentState = STATE_DISCONNECTED
    private var currentMode: String? = null
    private var currentConfigId = 0L

    @Synchronized
    fun snapshot(): XrayConnectionSnapshot {
        return XrayConnectionSnapshot(currentState, currentMode, currentConfigId)
    }

    fun publishConnecting(context: Context, mode: String, configId: Long = 0L) {
        publish(context, STATE_CONNECTING, mode, configId)
    }

    fun publishConnected(context: Context, mode: String, configId: Long = 0L) {
        publish(context, STATE_CONNECTED, mode, configId)
    }

    fun publishDisconnected(context: Context) {
        publish(context, STATE_DISCONNECTED, null, 0L)
    }

    @Synchronized
    private fun publish(context: Context, state: String, mode: String?, configId: Long) {
        currentState = state
        currentMode = mode
        currentConfigId = configId
        context.applicationContext.sendBroadcast(
            Intent(ACTION_CHANGED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_CONFIG_ID, configId)
        )
    }
}
