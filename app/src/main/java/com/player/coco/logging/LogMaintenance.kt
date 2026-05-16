package com.player.coco.logging

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object LogMaintenance {
    private const val INTERVAL_MILLIS = 30_000L
    private var appContext: Context? = null
    private var executor: ScheduledExecutorService? = null

    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        if (executor != null) {
            return
        }

        CocoLog.trimToBudget(context)
        executor = Executors.newSingleThreadScheduledExecutor().also { service ->
            service.scheduleWithFixedDelay(
                { appContext?.let { CocoLog.trimToBudget(it) } },
                INTERVAL_MILLIS,
                INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            )
        }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
        appContext?.let { CocoLog.trimToBudget(it) }
        appContext = null
    }
}
