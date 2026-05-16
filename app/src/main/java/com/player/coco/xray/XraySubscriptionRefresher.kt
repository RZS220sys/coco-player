package com.player.coco.xray

import com.player.coco.data.ChainLinkStore
import com.player.coco.logging.CocoLog
import com.player.coco.xray.runtime.XrayConnectionState
import com.player.coco.xray.runtime.XrayCoreRuntime

import android.content.Context
import java.util.Locale

class XraySubscriptionRefresher(
    context: Context,
    private val mode: String,
    private val configId: Long,
    private val includeTun: Boolean,
    initialConfigJson: String,
    private val tunFdProvider: () -> Int?,
    private val restartCore: (String, Int) -> Unit,
) {
    private val appContext = context.applicationContext
    private val configLock = Any()
    private var currentConfigJson = initialConfigJson

    @Volatile
    private var stopped = false
    private var thread: Thread? = null

    fun start() {
        if (thread != null) {
            return
        }

        thread = Thread(::runLoop, "CocoSubRefresh-$mode").also { it.start() }
    }

    fun stop() {
        stopped = true
        thread?.interrupt()
        thread = null
    }

    private fun runLoop() {
        while (!stopped) {
            val intervalMillis = updateIntervalMillis()
            if (intervalMillis == null) {
                CocoLog.info(appContext, TAG, "Subscription refresh disabled for $mode.")
                return
            }

            if (!sleepInterval(intervalMillis)) {
                return
            }

            if (!isConnected()) {
                continue
            }

            refreshOnce()
        }
    }

    private fun refreshOnce() {
        try {
            CocoLog.debug(appContext, TAG, "Checking subscription updates for $mode.")
            val nextConfigJson = XrayRuntimeConfigBuilder.build(
                appContext,
                includeTun,
                configId,
                writeGeneratedConfig = false
            )
            if (stopped || !isConnected()) {
                return
            }

            if (!configChanged(nextConfigJson)) {
                CocoLog.info(appContext, TAG, "Subscription configs unchanged; keeping running Xray core.")
                return
            }

            val tunFd = tunFdProvider()
            if (tunFd == null) {
                CocoLog.warning(appContext, TAG, "Skipping Xray restart because the TUN fd is not available.")
                return
            }

            restartCore(nextConfigJson, tunFd)
            XrayRuntimeConfigBuilder.writeGeneratedConfig(appContext, nextConfigJson)
            synchronized(configLock) {
                currentConfigJson = nextConfigJson
            }
            CocoLog.info(appContext, TAG, "Subscription configs changed; Xray core restarted.")
        } catch (error: Exception) {
            if (!stopped) {
                CocoLog.warning(appContext, TAG, "Subscription refresh failed; keeping current Xray core.", error)
            }
        }
    }

    private fun configChanged(nextConfigJson: String): Boolean {
        return synchronized(configLock) {
            currentConfigJson != nextConfigJson
        }
    }

    private fun isConnected(): Boolean {
        val snapshot = XrayConnectionState.snapshot()
        return snapshot.state == XrayConnectionState.STATE_CONNECTED &&
            snapshot.mode == mode &&
            XrayCoreRuntime.isRunning()
    }

    private fun updateIntervalMillis(): Long? {
        val settings = ChainLinkStore(appContext.filesDir).load(configId)?.settings
            ?: return DEFAULT_INTERVAL_MILLIS
        return parseIntervalMillis(settings.optString("subUpdateInterval", DEFAULT_INTERVAL_TEXT))
    }

    private fun sleepInterval(intervalMillis: Long): Boolean {
        return try {
            Thread.sleep(intervalMillis)
            true
        } catch (_: InterruptedException) {
            false
        }
    }

    private fun parseIntervalMillis(value: String): Long? {
        val normalized = value.trim().lowercase(Locale.US)
        if (normalized in setOf("0", "off", "disabled", "disable", "never")) {
            return null
        }
        if (normalized.isBlank()) {
            return DEFAULT_INTERVAL_MILLIS
        }

        val match = INTERVAL_PATTERN.matchEntire(normalized) ?: return DEFAULT_INTERVAL_MILLIS
        val amount = match.groupValues[1].toDoubleOrNull() ?: return DEFAULT_INTERVAL_MILLIS
        if (amount <= 0.0) {
            return null
        }

        val multiplier = when (match.groupValues[2]) {
            "ms" -> 1.0
            "s", "" -> 1000.0
            "m" -> 60_000.0
            "h" -> 3_600_000.0
            else -> 1000.0
        }
        return (amount * multiplier).toLong().coerceAtLeast(MIN_INTERVAL_MILLIS)
    }

    companion object {
        private const val TAG = "CocoSubRefresh"
        private const val DEFAULT_INTERVAL_TEXT = "30s"
        private const val DEFAULT_INTERVAL_MILLIS = 30_000L
        private const val MIN_INTERVAL_MILLIS = 5_000L
        private val INTERVAL_PATTERN = Regex("""^([0-9]+(?:\.[0-9]+)?)(ms|s|m|h)?$""")
    }
}
