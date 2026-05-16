package com.player.coco.xray.runtime

import com.player.coco.xray.service.XrayProxyOnlyService
import com.player.coco.xray.service.XrayVpnService

import android.content.Context
import android.content.Intent
import android.os.Build

object XrayServiceActions {
    const val ACTION_START = "com.player.coco.action.START_XRAY"
    const val ACTION_STOP = "com.player.coco.action.STOP_XRAY"
    const val ACTION_RESTART = "com.player.coco.action.RESTART_XRAY"
    const val EXTRA_CONFIG_ID = "com.player.coco.extra.CONFIG_ID"
    const val MODE_PROXY_ONLY = "Proxy-only"
    const val MODE_VPN = "VPN"

    fun startProxyOnly(context: Context, configId: Long) {
        startForeground(
            context,
            Intent(context, XrayProxyOnlyService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONFIG_ID, configId)
        )
    }

    fun startVpn(context: Context, configId: Long) {
        startForeground(
            context,
            Intent(context, XrayVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONFIG_ID, configId)
        )
    }

    fun restartVpn(context: Context, configId: Long) {
        startForeground(
            context,
            Intent(context, XrayVpnService::class.java)
                .setAction(ACTION_RESTART)
                .putExtra(EXTRA_CONFIG_ID, configId)
        )
    }

    fun stopAll(context: Context) {
        context.startService(Intent(context, XrayProxyOnlyService::class.java).setAction(ACTION_STOP))
        context.startService(Intent(context, XrayVpnService::class.java).setAction(ACTION_STOP))
    }

    private fun startForeground(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
