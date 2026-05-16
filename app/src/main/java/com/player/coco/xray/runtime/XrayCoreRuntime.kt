package com.player.coco.xray.runtime

import com.player.coco.logging.CocoLog

import android.content.Context
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

object XrayCoreRuntime {
    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0

        override fun shutdown(): Long {
            runningMode = null
            logContext?.let { CocoLog.info(it, TAG, "Xray core callback: shutdown.") }
            return 0
        }

        override fun onEmitStatus(p0: Long, p1: String?): Long {
            if (!p1.isNullOrBlank()) {
                logContext?.let { CocoLog.info(it, TAG, p1) }
            }
            return 0
        }
    }

    private var controller: CoreController? = null
    private var initialized = false
    private var runningMode: String? = null
    @Volatile
    private var logContext: Context? = null

    @Synchronized
    fun start(context: Context, configJson: String, tunFd: Int, mode: String) {
        init(context)
        val coreController = controller()
        if (coreController.getIsRunning()) {
            CocoLog.info(context, TAG, "Xray core already running; start request ignored.")
            return
        }

        CocoLog.info(context, TAG, "Starting Xray core in $mode mode.")
        coreController.startLoop(configJson, tunFd)
        if (!coreController.getIsRunning()) {
            error("Xray core did not report a running state.")
        }
        runningMode = mode
        CocoLog.info(context, TAG, "Xray core reported running.")
    }

    @Synchronized
    fun restart(context: Context, configJson: String, tunFd: Int, mode: String) {
        init(context)
        val coreController = controller()
        if (coreController.getIsRunning()) {
            CocoLog.info(context, TAG, "Stopping Xray core before restart.")
            coreController.stopLoop()
        }

        CocoLog.info(context, TAG, "Restarting Xray core in $mode mode.")
        coreController.startLoop(configJson, tunFd)
        if (!coreController.getIsRunning()) {
            runningMode = null
            error("Xray core did not report a running state after restart.")
        }
        runningMode = mode
        CocoLog.info(context, TAG, "Xray core restart complete.")
    }

    @Synchronized
    fun stop() {
        val coreController = controller
        if (coreController?.getIsRunning() == true) {
            coreController.stopLoop()
        }
        runningMode = null
    }

    @Synchronized
    fun isRunning(): Boolean = controller?.getIsRunning() == true

    @Synchronized
    fun mode(): String? = runningMode

    @Synchronized
    fun version(context: Context): String {
        init(context)
        return Libv2ray.checkVersionX()
    }

    private fun init(context: Context) {
        logContext = context.applicationContext
        if (initialized) {
            return
        }

        Seq.setContext(context.applicationContext)
        ensureXrayAssets(context)
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, XUDP_BASE_KEY)
        initialized = true
    }

    private fun ensureXrayAssets(context: Context) {
        context.filesDir.mkdirs()
        XRAY_ASSET_FILES.forEach { assetName ->
            val target = File(context.filesDir, assetName)
            if (target.exists() && target.length() > 0L) {
                return@forEach
            }

            context.assets.open(assetName).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun controller(): CoreController {
        return controller ?: Libv2ray.newCoreController(callback).also {
            controller = it
        }
    }

    private const val XUDP_BASE_KEY = "Y29jby14dWRwLWJhc2Uta2V5LTAwMDAwMDAwMDAwMDA"
    private const val TAG = "CocoXrayCore"
    private val XRAY_ASSET_FILES = arrayOf("geoip.dat", "geosite.dat")
}
