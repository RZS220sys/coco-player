package com.player.coco.xray.service

import com.player.coco.data.GlobalSettingsStore
import com.player.coco.data.PerAppSettingsStore
import com.player.coco.data.config.ConnectConfigStore
import com.player.coco.logging.CocoLog
import com.player.coco.logging.LogMaintenance
import com.player.coco.R
import com.player.coco.xray.runtime.XrayConnectionState
import com.player.coco.xray.runtime.XrayCoreRuntime
import com.player.coco.xray.runtime.XrayNotifications
import com.player.coco.xray.runtime.XrayServiceActions
import com.player.coco.xray.XrayRuntimeConfigBuilder
import com.player.coco.xray.XraySubscriptionRefresher
import org.json.JSONObject

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class XrayVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var subscriptionRefresher: XraySubscriptionRefresher? = null
    @Volatile
    private var stopRequested = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == XrayServiceActions.ACTION_STOP) {
            shutdown("Stop requested for VPN service.", stopService = true)
            return START_NOT_STICKY
        }

        val configId = intent?.getLongExtra(XrayServiceActions.EXTRA_CONFIG_ID, 0L) ?: 0L
        if (intent?.action == XrayServiceActions.ACTION_RESTART) {
            shutdown("Restart requested for VPN service.", stopService = false, publishDisconnected = false)
        }

        stopRequested = false
        stopSubscriptionRefresh()
        LogMaintenance.start(this)
        CocoLog.info(this, TAG, "Starting VPN service.")
        XrayConnectionState.publishConnecting(this, XrayServiceActions.MODE_VPN, configId)
        startForeground(
            XrayNotifications.NOTIFICATION_ID,
            XrayNotifications.build(this, getString(R.string.status_connecting))
        )

        Thread {
            try {
                val tun = establishVpn(configId)
                if (stopRequested) {
                    tun.close()
                    CocoLog.info(this, TAG, "VPN start cancelled after TUN establish.")
                    return@Thread
                }
                vpnInterface = tun
                val configJson = XrayRuntimeConfigBuilder.build(this, includeTun = true, configId = configId)
                if (stopRequested) {
                    tun.close()
                    CocoLog.info(this, TAG, "VPN start cancelled before core start.")
                    return@Thread
                }
                XrayCoreRuntime.start(this, configJson, tun.fd, XrayServiceActions.MODE_VPN)
                if (stopRequested) {
                    XrayCoreRuntime.stop()
                    tun.close()
                    CocoLog.info(this, TAG, "VPN start cancelled after core start.")
                    return@Thread
                }
                CocoLog.info(this, TAG, "VPN Xray core connected.")
                XrayConnectionState.publishConnected(this, XrayServiceActions.MODE_VPN, configId)
                startForeground(
                    XrayNotifications.NOTIFICATION_ID,
                    XrayNotifications.build(this, getString(R.string.status_connected_vpn))
                )
                startSubscriptionRefresh(configId, configJson)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start VPN Xray", error)
                CocoLog.error(this, TAG, "Failed to start VPN Xray.", error)
                XrayConnectionState.publishDisconnected(this)
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    override fun onRevoke() {
        shutdown("VPN permission revoked.", stopService = true)
    }

    override fun onDestroy() {
        shutdown("VPN service destroyed.", stopService = false)
        super.onDestroy()
    }

    private fun shutdown(reason: String, stopService: Boolean) {
        shutdown(reason, stopService, publishDisconnected = true)
    }

    private fun shutdown(reason: String, stopService: Boolean, publishDisconnected: Boolean) {
        CocoLog.info(this, TAG, reason)
        stopRequested = true
        stopSubscriptionRefresh()
        XrayCoreRuntime.stop()
        vpnInterface?.close()
        vpnInterface = null
        removeForegroundNotification()
        if (publishDisconnected) {
            XrayConnectionState.publishDisconnected(this)
        }
        LogMaintenance.stop()
        if (stopService) {
            stopSelf()
        }
    }

    private fun removeForegroundNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun establishVpn(configId: Long): ParcelFileDescriptor {
        val routingDns = effectiveRoutingDns(configId)
        val bypassPrivate = routingDns.optBoolean("bypassPrivate", true)

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress("10.10.0.2", 30)

        configurePerAppProxy(builder)

        if (bypassPrivate) {
            ROUTED_IP_LIST.forEach {
                val parts = it.split("/")
                builder.addRoute(parts[0], parts[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        XrayRuntimeConfigBuilder
            .parseDnsServers(routingDns.optString("dnsServers", "1.1.1.1 8.8.8.8"))
            .forEach { builder.addDnsServer(it) }

        return builder.establish() ?: error("Could not establish VPN interface.")
    }

    private fun effectiveRoutingDns(configId: Long): JSONObject {
        val globalSettings = GlobalSettingsStore(filesDir).load()
        val configSettings = ConnectConfigStore(filesDir).load(configId)?.data?.optJSONObject("settings")
        return configSettings?.optJSONObject("routingDns")
            ?: globalSettings.optJSONObject("routingDns")
            ?: JSONObject()
    }

    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = packageName
        val settings = PerAppSettingsStore(filesDir).load()

        if (!settings.enabled) {
            addDisallowedApplication(builder, selfPackageName)
            CocoLog.info(this, TAG, "Per-app VPN is disabled; excluding app package $selfPackageName.")
            return
        }

        val apps = settings.selectedPackages.toMutableSet()
        if (apps.isEmpty()) {
            addDisallowedApplication(builder, selfPackageName)
            CocoLog.info(this, TAG, "Per-app VPN is enabled with no selected apps; excluding app package $selfPackageName.")
            return
        }

        if (settings.bypassMode) {
            apps.add(selfPackageName)
        } else {
            apps.remove(selfPackageName)
        }

        var appliedCount = 0
        apps.forEach { appPackage ->
            try {
                if (settings.bypassMode) {
                    builder.addDisallowedApplication(appPackage)
                } else {
                    builder.addAllowedApplication(appPackage)
                }
                appliedCount += 1
            } catch (error: PackageManager.NameNotFoundException) {
                CocoLog.warning(this, TAG, "Skipping missing per-app package: $appPackage", error)
            }
        }

        CocoLog.info(
            this,
            TAG,
            "Per-app VPN applied: mode=${if (settings.bypassMode) "bypass" else "include"}, packages=$appliedCount."
        )
    }

    private fun addDisallowedApplication(builder: Builder, appPackage: String) {
        try {
            builder.addDisallowedApplication(appPackage)
        } catch (error: PackageManager.NameNotFoundException) {
            CocoLog.warning(this, TAG, "Skipping missing disallowed package: $appPackage", error)
        }
    }

    private fun startSubscriptionRefresh(configId: Long, initialConfigJson: String) {
        subscriptionRefresher = XraySubscriptionRefresher(
            context = this,
            mode = XrayServiceActions.MODE_VPN,
            configId = configId,
            includeTun = true,
            initialConfigJson = initialConfigJson,
            tunFdProvider = { vpnInterface?.fd },
            restartCore = { nextConfigJson, tunFd ->
                if (stopRequested) {
                    error("VPN service is stopping.")
                }
                try {
                    XrayCoreRuntime.restart(this, nextConfigJson, tunFd, XrayServiceActions.MODE_VPN)
                } catch (error: Exception) {
                    CocoLog.error(this, TAG, "Failed to restart VPN Xray after subscription update.", error)
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
        private const val TAG = "CocoXrayVpn"

        private val ROUTED_IP_LIST = arrayOf(
            "0.0.0.0/5",
            "8.0.0.0/7",
            "11.0.0.0/8",
            "12.0.0.0/6",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/2",
            "128.0.0.0/3",
            "160.0.0.0/5",
            "168.0.0.0/6",
            "172.0.0.0/12",
            "172.32.0.0/11",
            "172.64.0.0/10",
            "172.128.0.0/9",
            "173.0.0.0/8",
            "174.0.0.0/7",
            "176.0.0.0/4",
            "192.0.0.0/9",
            "192.128.0.0/11",
            "192.160.0.0/13",
            "192.169.0.0/16",
            "192.170.0.0/15",
            "192.172.0.0/14",
            "192.176.0.0/12",
            "192.192.0.0/10",
            "193.0.0.0/8",
            "194.0.0.0/7",
            "196.0.0.0/6",
            "200.0.0.0/5",
            "208.0.0.0/4",
            "240.0.0.0/4"
        )
    }
}
