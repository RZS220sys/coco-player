package com.player.coco.xray

import com.player.coco.data.ConnectCryptoSession
import com.player.coco.data.ConnectDataCipher
import com.player.coco.data.GlobalSettingsStore
import com.player.coco.data.MusicSettingsStore
import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigStore
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.chainlink.ChainLinkConfigData
import com.player.coco.data.config.chainlink.ChainLinkConfigDataMapper
import com.player.coco.data.config.singlelink.SingleLinkConfigData
import com.player.coco.data.config.singlelink.SingleLinkConfigDataMapper
import com.player.coco.logging.CocoLog
import com.player.coco.xray.single.SingleLinkOutboundBuilder

import android.content.Context
import android.util.AtomicFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

object XrayRuntimeConfigBuilder {
    private const val FRONT_PREFIX = "front-"
    private const val FRONT_BALANCER = "front-pool"
    private const val EXIT_TAG = "exit"
    private const val CHAIN_DIALER_TAG = "chain-dialer"
    private const val CHAIN_IN_TAG = "chain-in"
    private const val DIRECT_TAG = "direct"
    private const val BLOCK_TAG = "block"

    fun build(
        context: Context,
        includeTun: Boolean,
        configId: Long = 0L,
        writeGeneratedConfig: Boolean = true,
    ): String {
        val config = selectedConfig(context, configId)
        val configName = configName(config)
        CocoLog.info(context, TAG, "Building Xray config for '$configName' (${config.type}).")
        val globalSettings = GlobalSettingsStore(context.filesDir).load()
        val configSettings = configSettings(config)
        val localInbounds = effectiveSection(configSettings, globalSettings, "localInbounds")
        val routingDns = effectiveSection(configSettings, globalSettings, "routingDns")
        val runtime = effectiveSection(configSettings, globalSettings, "runtime")

        val json = when (config.type) {
            ConnectConfigTypes.CHAIN_LINK -> buildChainLinkConfig(
                context = context,
                config = ChainLinkConfigDataMapper.fromContainer(config)
                    ?: error("Invalid chain-link config data."),
                localInbounds = localInbounds,
                routingDns = routingDns,
                runtime = runtime,
                configSettings = configSettings,
                includeTun = includeTun,
            )
            ConnectConfigTypes.SINGLE_LINK -> buildSingleLinkConfig(
                context = context,
                config = SingleLinkConfigDataMapper.fromContainer(config)
                    ?: error("Invalid single-link config data."),
                localInbounds = localInbounds,
                routingDns = routingDns,
                runtime = runtime,
                configSettings = configSettings,
                includeTun = includeTun,
            )
            else -> error("Unsupported config type: ${config.type}")
        }.toString(2)

        if (writeGeneratedConfig) {
            writeGeneratedConfig(context, json)
        }
        CocoLog.info(context, TAG, "Generated Xray config for '$configName' (${config.type}).")
        return json
    }

    private fun buildChainLinkConfig(
        context: Context,
        config: ChainLinkConfigData,
        localInbounds: JSONObject,
        routingDns: JSONObject,
        runtime: JSONObject,
        configSettings: JSONObject,
        includeTun: Boolean,
    ): JSONObject {
        val exitOutbound = XrayShareLinkParser.parse(config.exitUri).outbound
        val frontends = parseFrontends(context, config, configSettings, routingDns)
        if (frontends.isEmpty()) {
            error("Subscription yielded no usable frontend configs.")
        }

        return buildConfig(
            context = context,
            localInbounds = localInbounds,
            routingDns = routingDns,
            runtime = runtime,
            chainSettings = configSettings,
            includeTun = includeTun,
            frontendOutbounds = frontends,
            exitOutbound = exitOutbound,
        )
    }

    private fun buildSingleLinkConfig(
        context: Context,
        config: SingleLinkConfigData,
        localInbounds: JSONObject,
        routingDns: JSONObject,
        runtime: JSONObject,
        configSettings: JSONObject,
        includeTun: Boolean,
    ): JSONObject {
        val outbound = SingleLinkOutboundBuilder.build(config.protocol, config.values)
        return buildConfig(
            context = context,
            localInbounds = localInbounds,
            routingDns = routingDns,
            runtime = runtime,
            chainSettings = configSettings,
            includeTun = includeTun,
            frontendOutbounds = emptyList(),
            exitOutbound = outbound,
        )
    }

    private fun selectedConfig(context: Context, configId: Long): ConnectConfigContainer {
        val store = ConnectConfigStore(context.filesDir)
        return if (configId > 0L) {
            store.load(configId)
        } else {
            store.loadAll().firstOrNull()
        } ?: error("No connect config is available.")
    }

    private fun configName(config: ConnectConfigContainer): String {
        return when (config.type) {
            ConnectConfigTypes.CHAIN_LINK -> ChainLinkConfigDataMapper.fromContainer(config)?.name
            ConnectConfigTypes.SINGLE_LINK -> SingleLinkConfigDataMapper.fromContainer(config)?.name
            else -> config.data.optString("name")
        }.orEmpty().ifBlank { config.type }
    }

    private fun configSettings(config: ConnectConfigContainer): JSONObject {
        return when (config.type) {
            ConnectConfigTypes.CHAIN_LINK -> ChainLinkConfigDataMapper.fromContainer(config)?.settings
            ConnectConfigTypes.SINGLE_LINK -> SingleLinkConfigDataMapper.fromContainer(config)?.settings
            else -> config.data.optJSONObject("settings")
        } ?: JSONObject()
    }

    private fun parseFrontends(
        context: Context,
        config: ChainLinkConfigData,
        chainSettings: JSONObject,
        routingDns: JSONObject,
    ): List<JSONObject> {
        val timeoutSeconds = stringSetting(chainSettings, "fetchTimeout", "20.0").toDoubleOrNull() ?: 20.0
        val maxFronts = stringSetting(chainSettings, "maxFronts", "200").toIntOrNull() ?: 200
        val strict = chainSettings.optBoolean("strict", false)
        CocoLog.debug(
            context,
            TAG,
            "Fetching frontend configs from ${logSource(config.subUrl)} with timeout=${timeoutSeconds}s maxFronts=$maxFronts strict=$strict."
        )
        val text = fetchSubscription(context, config.subUrl, timeoutSeconds, routingDns)
        CocoLog.debug(context, TAG, "Subscription content loaded: ${text.length} characters.")
        val seen = linkedSetOf<String>()
        val parsed = mutableListOf<JSONObject>()
        val links = XrayShareLinkParser.extractLinks(text)
        var duplicateCount = 0
        var unsupportedCount = 0
        var parseErrorCount = 0
        var firstParseError = ""

        CocoLog.debug(context, TAG, "Extracted ${links.size} candidate frontend links from subscription.")
        links.forEach { link ->
            if (maxFronts > 0 && parsed.size >= maxFronts) return@forEach

            val key = XrayShareLinkParser.canonicalWithoutFragment(link)
            if (!seen.add(key)) {
                duplicateCount += 1
                return@forEach
            }

            val outbound = runCatching { XrayShareLinkParser.parse(link).outbound }
                .getOrElse { error ->
                    if (strict) throw error
                    parseErrorCount += 1
                    if (firstParseError.isBlank()) {
                        firstParseError = error.message.orEmpty()
                    }
                    return@forEach
                }

            if (outbound.optString("protocol") in setOf("vless", "vmess", "trojan", "shadowsocks")) {
                parsed.add(outbound)
            } else {
                unsupportedCount += 1
            }
        }

        CocoLog.debug(
            context,
            TAG,
            "Frontend parse result: usable=${parsed.size}, duplicates=$duplicateCount, unsupported=$unsupportedCount, parseErrors=$parseErrorCount."
        )
        if (firstParseError.isNotBlank()) {
            CocoLog.debug(context, TAG, "First frontend parse error: $firstParseError")
        }
        return parsed
    }

    private fun fetchSubscription(
        context: Context,
        urlOrPath: String,
        timeoutSeconds: Double,
        routingDns: JSONObject,
    ): String {
        val value = urlOrPath.trim()
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            val timeoutMillis = (timeoutSeconds * 1000).toLong().coerceAtLeast(1000L)
            val domesticDnsServers = parseDnsServers(routingDns.optString("domesticDnsServers", ""))
            CocoLog.debug(context, TAG, "Opening subscription HTTP request: ${logSource(value)}")
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            if (domesticDnsServers.isNotEmpty()) {
                CocoLog.info(
                    context,
                    TAG,
                    "Fetching subscription with domestic DNS: ${domesticDnsServers.joinToString(", ")}"
                )
                clientBuilder.dns(
                    DomesticDnsResolver(
                        domesticDnsServers,
                        useIpv4Only = routingDns.optBoolean("useIpv4", true)
                    )
                )
            }

            val request = Request.Builder()
                .url(value)
                .header("User-Agent", "coco-player/0.1")
                .header("Accept", "text/plain,*/*")
                .build()

            clientBuilder.build().newCall(request).execute().use { response ->
                CocoLog.debug(context, TAG, "Subscription HTTP response: code=${response.code}")
                if (!response.isSuccessful) {
                    error("Subscription fetch failed with HTTP ${response.code}.")
                }
                return response.body?.string().orEmpty()
            }
        }

        val file = if (value.startsWith("file://", true)) {
            File(URI(value))
        } else {
            File(value)
        }
        CocoLog.debug(context, TAG, "Reading frontend configs from local file: ${file.absolutePath}")
        return file.readText(Charsets.UTF_8)
    }

    private fun buildConfig(
        context: Context,
        localInbounds: JSONObject,
        routingDns: JSONObject,
        runtime: JSONObject,
        chainSettings: JSONObject,
        includeTun: Boolean,
        frontendOutbounds: List<JSONObject>,
        exitOutbound: JSONObject,
    ): JSONObject {
        val isChainLink = frontendOutbounds.isNotEmpty()
        val inboundsResult = buildInbounds(localInbounds, routingDns, includeTun, includeChainInbound = isChainLink)
        val taggedExit = if (isChainLink) addExitDialerProxy(exitOutbound) else addTag(exitOutbound, EXIT_TAG)
        val taggedFrontends = frontendOutbounds.mapIndexed { index, outbound ->
            addTag(outbound, "%s%04d".format(FRONT_PREFIX, index + 1))
        }

        val outbounds = JSONArray()
            .put(taggedExit)

        taggedFrontends.forEach { outbounds.put(it) }
        if (isChainLink) {
            outbounds.put(chainDialer(localInbounds))
        }
        outbounds
            .put(directOutbound(routingDns))
            .put(blockOutbound())

        val config = JSONObject()
            .put("log", buildLog(context, runtime))
            .put("dns", buildDns(routingDns, bootstrapDomains(taggedExit, taggedFrontends)))
            .put("inbounds", inboundsResult.inbounds)
            .put("outbounds", outbounds)
            .put("routing", buildRouting(routingDns, chainSettings, inboundsResult.mainTags, taggedFrontends.size))
            .put(
                "policy",
                JSONObject()
                    .put(
                        "levels",
                        JSONObject().put(
                            "8",
                            JSONObject()
                                .put("handshake", 4)
                                .put("connIdle", 300)
                                .put("uplinkOnly", 1)
                                .put("downlinkOnly", 1)
                        )
                    )
                    .put(
                        "system",
                        JSONObject()
                            .put("statsOutboundUplink", true)
                            .put("statsOutboundDownlink", true)
                    )
            )
            .put("stats", JSONObject())

        if (isChainLink) {
            buildObservatory(chainSettings)?.let { (key, value) -> config.put(key, value) }
        }
        return config
    }

    private fun buildLog(context: Context, runtime: JSONObject): JSONObject {
        val level = CocoLog.xrayLogLevel(context)
            .ifBlank { runtime.optString("loglevel", "warning") }
            .ifBlank { "warning" }
        val log = JSONObject()
            .put("loglevel", level)

        if (CocoLog.xrayAccessLogsEnabled(context)) {
            log.put("access", CocoLog.xrayAccessLogPath(context))
        }

        if (level == CocoLog.LEVEL_DEBUG) {
            log.put("dnsLog", true)
        }

        CocoLog.info(
            context,
            TAG,
            "Xray log level set to $level; access logs ${if (CocoLog.xrayAccessLogsEnabled(context)) "enabled" else "disabled"}."
        )
        return log
    }

    private fun buildInbounds(
        localInbounds: JSONObject,
        routingDns: JSONObject,
        includeTun: Boolean,
        includeChainInbound: Boolean,
    ): InboundsResult {
        val listen = localInbounds.optString("listen", "127.0.0.1").ifBlank { "127.0.0.1" }
        val inboundType = localInbounds.optString("inbounds", "socks").ifBlank { "socks" }
        val socksPort = localInbounds.optString("socksPort", "10809").toIntOrNull() ?: 10809
        val httpPort = localInbounds.optString("httpPort", "10809").toIntOrNull() ?: 10809
        val internalPort = localInbounds.optString("internalPort", "10990").toIntOrNull() ?: 10990
        val sniffRouteOnly = routingDns.optBoolean("sniffRouteOnly", true)
        val inbounds = JSONArray()
        val mainTags = mutableListOf<String>()

        if (inboundType == "both" || inboundType == "socks") {
            inbounds.put(socksInbound("socks-in", listen, socksPort, sniffRouteOnly))
            mainTags.add("socks-in")
        }

        if (inboundType == "both" || inboundType == "http") {
            inbounds.put(httpInbound("http-in", listen, httpPort, sniffRouteOnly))
            mainTags.add("http-in")
        }

        if (inboundType == "mixed") {
            inbounds.put(mixedInbound("mixed-in", listen, socksPort, sniffRouteOnly))
            mainTags.add("mixed-in")
        }

        if (includeTun) {
            inbounds.put(tunInbound(sniffRouteOnly))
            mainTags.add("tun")
        }

        if (includeChainInbound) {
            inbounds.put(socksInbound(CHAIN_IN_TAG, "127.0.0.1", internalPort, routeOnly = true, sniffingEnabled = false))
        }

        if (mainTags.isEmpty()) {
            error("No main inbound tags were generated.")
        }

        return InboundsResult(inbounds, mainTags)
    }

    private fun socksInbound(
        tag: String,
        listen: String,
        port: Int,
        routeOnly: Boolean,
        sniffingEnabled: Boolean = true,
    ): JSONObject {
        return JSONObject()
            .put("tag", tag)
            .put("listen", listen)
            .put("port", port)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth").put("userLevel", 8))
            .put("sniffing", sniffing(sniffingEnabled, routeOnly))
    }

    private fun httpInbound(tag: String, listen: String, port: Int, routeOnly: Boolean): JSONObject {
        return JSONObject()
            .put("tag", tag)
            .put("listen", listen)
            .put("port", port)
            .put("protocol", "http")
            .put("settings", JSONObject())
            .put("sniffing", sniffing(enabled = true, routeOnly = routeOnly))
    }

    private fun mixedInbound(tag: String, listen: String, port: Int, routeOnly: Boolean): JSONObject {
        return JSONObject()
            .put("tag", tag)
            .put("listen", listen)
            .put("port", port)
            .put("protocol", "mixed")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth").put("userLevel", 8))
            .put("sniffing", sniffing(enabled = true, routeOnly = routeOnly))
    }

    private fun tunInbound(routeOnly: Boolean): JSONObject {
        return JSONObject()
            .put("tag", "tun")
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "coco0").put("MTU", 1500).put("userLevel", 8))
            .put("sniffing", sniffing(enabled = true, routeOnly = routeOnly))
    }

    private fun sniffing(enabled: Boolean, routeOnly: Boolean): JSONObject {
        return JSONObject()
            .put("enabled", enabled)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            .put("routeOnly", routeOnly)
    }

    private fun buildRouting(
        routingDns: JSONObject,
        chainSettings: JSONObject,
        mainTags: List<String>,
        frontendCount: Int,
    ): JSONObject {
        val rules = JSONArray()

        if (frontendCount > 0) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put(CHAIN_IN_TAG))
                    .put("balancerTag", FRONT_BALANCER)
            )
        }

        if (routingDns.optBoolean("blockUdp443", false)) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray(mainTags))
                    .put("network", "udp")
                    .put("port", "443")
                    .put("outboundTag", BLOCK_TAG)
            )
        }

        if (routingDns.optBoolean("bypassPrivate", true)) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray(mainTags))
                    .put("domain", JSONArray().put("geosite:private"))
                    .put("outboundTag", DIRECT_TAG)
            )
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray(mainTags))
                    .put("ip", JSONArray().put("geoip:private"))
                    .put("outboundTag", DIRECT_TAG)
            )
        }

        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray(mainTags))
                .put("outboundTag", EXIT_TAG)
        )

        val routing = JSONObject()
            .put("domainStrategy", routingDns.optString("domainStrategy", "IPIfNonMatch").ifBlank { "IPIfNonMatch" })
            .put("rules", rules)

        if (frontendCount > 0) {
            val strategyType = stringSetting(chainSettings, "balancerStrategy", "leastLoad")
            val strategy = JSONObject().put("type", strategyType)
            if (strategyType == "leastLoad") {
                val settings = JSONObject().put("expected", stringSetting(chainSettings, "expected", "4").toIntOrNull() ?: 4)
                stringSetting(chainSettings, "maxRtt", "").takeIf { it.isNotBlank() }?.let { settings.put("maxRTT", it) }
                stringSetting(chainSettings, "baseline", "").takeIf { it.isNotBlank() }?.let { settings.put("baselines", JSONArray().put(it)) }
                strategy.put("settings", settings)
            }

            val balancer = JSONObject()
                .put("tag", FRONT_BALANCER)
                .put("selector", JSONArray().put(FRONT_PREFIX))
                .put("fallbackTag", "%s%04d".format(FRONT_PREFIX, 1.coerceAtMost(frontendCount)))
                .put("strategy", strategy)

            routing.put("balancers", JSONArray().put(balancer))
        }

        return routing
    }

    private fun buildObservatory(chainSettings: JSONObject): Pair<String, JSONObject>? {
        return when (stringSetting(chainSettings, "observer", "burst")) {
            "off" -> null
            "regular" -> "observatory" to JSONObject()
                .put("subjectSelector", JSONArray().put(FRONT_PREFIX))
                .put("probeUrl", stringSetting(chainSettings, "probeUrl", "https://connectivitycheck.gstatic.com/generate_204"))
                .put("probeInterval", stringSetting(chainSettings, "probeInterval", "10s"))
                .put("enableConcurrency", true)

            else -> "burstObservatory" to JSONObject()
                .put("subjectSelector", JSONArray().put(FRONT_PREFIX))
                .put(
                    "pingConfig",
                    JSONObject()
                        .put("destination", stringSetting(chainSettings, "probeUrl", "https://connectivitycheck.gstatic.com/generate_204"))
                        .put("connectivity", stringSetting(chainSettings, "connectivityUrl", ""))
                        .put("interval", stringSetting(chainSettings, "probeInterval", "10s"))
                        .put("timeout", stringSetting(chainSettings, "probeTimeout", "5s"))
                        .put("sampling", stringSetting(chainSettings, "probeSampling", "2").toIntOrNull() ?: 2)
                        .put("httpMethod", stringSetting(chainSettings, "probeMethod", "HEAD"))
                )
        }
    }

    private fun buildDns(routingDns: JSONObject, bootstrapDomains: List<String>): JSONObject {
        val servers = JSONArray()
        val domesticDnsServers = parseDnsServers(routingDns.optString("domesticDnsServers", ""))
        if (domesticDnsServers.isNotEmpty() && bootstrapDomains.isNotEmpty()) {
            val domains = JSONArray()
            bootstrapDomains.forEach { domains.put("full:$it") }
            domesticDnsServers.forEach { server ->
                servers.put(
                    JSONObject()
                        .put("address", server)
                        .put("domains", domains)
                    )
            }
        }
        parseDnsServers(routingDns.optString("dnsServers", "1.1.1.1 8.8.8.8")).forEach { servers.put(it) }
        return JSONObject()
            .put("servers", servers)
            .put("queryStrategy", if (routingDns.optBoolean("useIpv4", true)) "UseIPv4" else "UseIP")
    }

    private fun bootstrapDomains(exitOutbound: JSONObject, frontendOutbounds: List<JSONObject>): List<String> {
        val hosts = linkedSetOf<String>()
        hosts.addAll(outboundServerHosts(exitOutbound))
        frontendOutbounds.forEach { hosts.addAll(outboundServerHosts(it)) }
        return hosts
            .map { it.trim().trimEnd('.').lowercase() }
            .filter { it.isNotBlank() && !isIpLiteral(it) }
            .toList()
    }

    private fun outboundServerHosts(outbound: JSONObject): List<String> {
        val settings = outbound.optJSONObject("settings") ?: return emptyList()
        val hosts = mutableListOf<String>()
        settings.optJSONArray("vnext")?.let { vnext ->
            for (index in 0 until vnext.length()) {
                vnext.optJSONObject(index)?.optString("address")?.takeIf { it.isNotBlank() }?.let { hosts.add(it) }
            }
        }
        settings.optJSONArray("servers")?.let { servers ->
            for (index in 0 until servers.length()) {
                servers.optJSONObject(index)?.optString("address")?.takeIf { it.isNotBlank() }?.let { hosts.add(it) }
            }
        }
        return hosts
    }

    private fun addExitDialerProxy(exitOutbound: JSONObject): JSONObject {
        val outbound = JSONObject(exitOutbound.toString()).put("tag", EXIT_TAG)
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = stream.optJSONObject("sockopt") ?: JSONObject().also { stream.put("sockopt", it) }
        sockopt.put("dialerProxy", CHAIN_DIALER_TAG)
        return outbound
    }

    private fun addTag(outbound: JSONObject, tag: String): JSONObject {
        return JSONObject(outbound.toString()).put("tag", tag)
    }

    private fun chainDialer(localInbounds: JSONObject): JSONObject {
        val internalPort = localInbounds.optString("internalPort", "10990").toIntOrNull() ?: 10990
        return JSONObject()
            .put("tag", CHAIN_DIALER_TAG)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("address", "127.0.0.1").put("port", internalPort))
    }

    private fun directOutbound(routingDns: JSONObject): JSONObject {
        return JSONObject()
            .put("tag", DIRECT_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject().put("domainStrategy", if (routingDns.optBoolean("useIpv4", true)) "UseIPv4" else "AsIs")
            )
    }

    private fun blockOutbound(): JSONObject {
        return JSONObject()
            .put("tag", BLOCK_TAG)
            .put("protocol", "blackhole")
            .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))
    }

    private fun effectiveSection(configSettings: JSONObject, globalSettings: JSONObject, key: String): JSONObject {
        return configSettings.optJSONObject(key) ?: globalSettings.optJSONObject(key) ?: JSONObject()
    }

    private fun stringSetting(settings: JSONObject, key: String, default: String): String {
        return settings.optString(key, default).ifBlank { default }
    }

    fun writeGeneratedConfig(context: Context, json: String) {
        writeGeneratedConfigFile(context.filesDir, json)
    }

    private fun writeGeneratedConfigFile(filesDir: File, json: String) {
        val atomicFile = AtomicFile(File(filesDir, ConnectConfigStore.GENERATED_XRAY_CONFIG_NAME))
        val output = atomicFile.startWrite()
        try {
            val dataToWrite = if (MusicSettingsStore(filesDir).hasCocoConnectAuth()) {
                val key = ConnectCryptoSession.keyOrNull()
                    ?: throw IllegalStateException("Coco connect data is locked.")
                ConnectDataCipher.serializeJson(JSONObject(json), key)
            } else {
                json
            }
            output.write(dataToWrite.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun parseDnsServers(value: String): List<String> {
        return value
            .split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun isIpLiteral(value: String): Boolean {
        return IPV4_PATTERN.matches(value) || ":" in value
    }

    private fun logSource(value: String): String {
        return runCatching {
            val uri = URI(value.trim())
            if (uri.scheme == null || uri.host == null) {
                value.trim()
            } else {
                "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}${uri.path.orEmpty()}"
            }
        }.getOrDefault(value.trim())
    }

    private data class InboundsResult(
        val inbounds: JSONArray,
        val mainTags: List<String>,
    )

    private const val TAG = "CocoXrayConfig"
    private val IPV4_PATTERN = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
}
