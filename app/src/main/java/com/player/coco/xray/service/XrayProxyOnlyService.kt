package com.player.coco.xray.service

import com.player.coco.logging.CocoLog
import com.player.coco.logging.LogMaintenance
import com.player.coco.R
import com.player.coco.xray.runtime.XrayConnectionState
import com.player.coco.xray.runtime.XrayCoreRuntime
import com.player.coco.xray.runtime.XrayNotifications
import com.player.coco.xray.runtime.XrayServiceActions
import com.player.coco.xray.XrayRuntimeConfigBuilder
import com.player.coco.xray.XraySubscriptionRefresher

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class XrayProxyOnlyService : Service() {
    @Volatile
    private var stopRequested = false
    private var subscriptionRefresher: XraySubscriptionRefresher? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == XrayServiceActions.ACTION_STOP) {
            shutdown("Stop requested for proxy-only service.", stopService = true)
            return START_NOT_STICKY
        }

        stopRequested = false
        stopSubscriptionRefresh()
        LogMaintenance.start(this)
        CocoLog.info(this, TAG, "Starting proxy-only service.")
        val configId = intent?.getLongExtra(XrayServiceActions.EXTRA_CONFIG_ID, 0L) ?: 0L
        XrayConnectionState.publishConnecting(this, XrayServiceActions.MODE_PROXY_ONLY, configId)
        startForeground(
            XrayNotifications.NOTIFICATION_ID,
            XrayNotifications.build(this, getString(R.string.status_connecting))
        )

        Thread {
            try {
                val configJson = XrayRuntimeConfigBuilder.build(this, includeTun = false, configId = configId)
                if (stopRequested) {
                    CocoLog.info(this, TAG, "Proxy-only start cancelled before core start.")
                    return@Thread
                }
                XrayCoreRuntime.start(this, configJson, tunFd = 0, mode = XrayServiceActions.MODE_PROXY_ONLY)
                if (stopRequested) {
                    XrayCoreRuntime.stop()
                    CocoLog.info(this, TAG, "Proxy-only start cancelled after core start.")
                    return@Thread
                }
                CocoLog.info(this, TAG, "Proxy-only Xray core connected.")
                XrayConnectionState.publishConnected(this, XrayServiceActions.MODE_PROXY_ONLY, configId)
                startForeground(
                    XrayNotifications.NOTIFICATION_ID,
                    XrayNotifications.build(this, getString(R.string.status_connected_proxy))
                )
                startSubscriptionRefresh(configId, configJson)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start proxy-only Xray", error)
                CocoLog.error(this, TAG, "Failed to start proxy-only Xray.", error)
                XrayConnectionState.publishDisconnected(this)
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        shutdown("Proxy-only service destroyed.", stopService = false)
        super.onDestroy()
    }

    private fun shutdown(reason: String, stopService: Boolean) {
        CocoLog.info(this, TAG, reason)
        stopRequested = true
        stopSubscriptionRefresh()
        XrayCoreRuntime.stop()
        removeForegroundNotification()
        XrayConnectionState.publishDisconnected(this)
        LogMaintenance.stop()
        if (stopService) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun removeForegroundNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun startSubscriptionRefresh(configId: Long, initialConfigJson: String) {
        subscriptionRefresher = XraySubscriptionRefresher(
            context = this,
            mode = XrayServiceActions.MODE_PROXY_ONLY,
            configId = configId,
            includeTun = false,
            initialConfigJson = initialConfigJson,
            tunFdProvider = { 0 },
            restartCore = { nextConfigJson, tunFd ->
                if (stopRequested) {
                    error("Proxy-only service is stopping.")
                }
                try {
                    XrayCoreRuntime.restart(this, nextConfigJson, tunFd, XrayServiceActions.MODE_PROXY_ONLY)
                } catch (error: Exception) {
                    CocoLog.error(this, TAG, "Failed to restart proxy-only Xray after subscription update.", error)
                    XrayConnectionState.publishDisconnected(this)
                    stopSelf()
                    throw error
                }
            }
        ).also { it.start() }
    }

    private fun stopSubscriptionRefresh() {
        subscriptionRefresher?.stop()
        subscriptionRefresher = null
    }

    companion object {
        private const val TAG = "CocoXrayProxy"
    }
}
