package com.player.coco.xray

import android.content.Context
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object ConfigLatencyTester {
    fun measureRealDelay(context: Context, configId: Long, targetUrl: String): Int {
        return try {
            XrayRuntimeConfigBuilder.build(
                context = context,
                includeTun = false,
                configId = configId,
                writeGeneratedConfig = false,
            ).let { configJson ->
                com.player.coco.xray.runtime.XrayCoreRuntime
                    .measureOutboundDelay(context, configForOutboundDelay(configJson), targetUrl)
                    .toInt()
            }
        } catch (_: Exception) {
            -1
        }
    }

    fun measureTcping(context: Context, configId: Long): Int {
        return try {
            val configJson = XrayRuntimeConfigBuilder.build(
                context = context,
                includeTun = false,
                configId = configId,
                writeGeneratedConfig = false,
            )
            val endpoint = tcpEndpoint(JSONObject(configJson)) ?: return -1
            tcpConnect(endpoint.host, endpoint.port)
        } catch (_: Exception) {
            -1
        }
    }

    private fun tcpEndpoint(config: JSONObject): TcpEndpoint? {
        val outbounds = config.optJSONArray("outbounds") ?: return null
        val preferredTags = listOf("front-0001", "exit")
        preferredTags.forEach { tag ->
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                if (outbound.optString("tag") == tag) {
                    outboundEndpoint(outbound)?.let { return it }
                }
            }
        }

        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            outboundEndpoint(outbound)?.let { return it }
        }
        return null
    }

    private fun configForOutboundDelay(configJson: String): String {
        val config = JSONObject(configJson)
        val outbounds = config.optJSONArray("outbounds") ?: return configJson
        if (findOutbound(outbounds, FIRST_FRONTEND_TAG) == null) {
            return configJson
        }

        val exit = findOutbound(outbounds, EXIT_TAG) ?: return configJson
        val stream = exit.optJSONObject("streamSettings") ?: return configJson
        val sockopt = stream.optJSONObject("sockopt") ?: return configJson
        if (sockopt.optString("dialerProxy") == CHAIN_DIALER_TAG) {
            sockopt.put("dialerProxy", FIRST_FRONTEND_TAG)
        }
        return config.toString()
    }

    private fun findOutbound(outbounds: org.json.JSONArray, tag: String): JSONObject? {
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("tag") == tag) {
                return outbound
            }
        }
        return null
    }

    private fun outboundEndpoint(outbound: JSONObject): TcpEndpoint? {
        val settings = outbound.optJSONObject("settings") ?: return null
        settings.optJSONArray("vnext")?.let { vnext ->
            for (index in 0 until vnext.length()) {
                val server = vnext.optJSONObject(index) ?: continue
                endpointFromServerObject(server)?.let { return it }
            }
        }
        settings.optJSONArray("servers")?.let { servers ->
            for (index in 0 until servers.length()) {
                val server = servers.optJSONObject(index) ?: continue
                endpointFromServerObject(server)?.let { return it }
            }
        }
        return null
    }

    private fun endpointFromServerObject(server: JSONObject): TcpEndpoint? {
        val host = server.optString("address").trim()
        val port = server.optInt("port", 0)
        if (host.isBlank() || port <= 0) {
            return null
        }
        return TcpEndpoint(host, port)
    }

    private fun tcpConnect(host: String, port: Int): Int {
        val elapsed = measureTimeMillis {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MILLIS)
            }
        }
        return elapsed.toInt().coerceAtLeast(0)
    }

    private data class TcpEndpoint(
        val host: String,
        val port: Int,
    )

    private const val TCP_CONNECT_TIMEOUT_MILLIS = 12_000
    private const val EXIT_TAG = "exit"
    private const val FIRST_FRONTEND_TAG = "front-0001"
    private const val CHAIN_DIALER_TAG = "chain-dialer"
}
