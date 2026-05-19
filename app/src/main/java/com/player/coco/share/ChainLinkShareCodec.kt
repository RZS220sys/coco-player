package com.player.coco.share

import android.util.Base64
import com.player.coco.data.config.ConnectConfigContainer
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.chainlink.ChainLinkConfigData
import com.player.coco.data.config.chainlink.ChainLinkConfigDataMapper
import com.player.coco.data.config.chainlink.ChainLinkDraft
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object ChainLinkShareCodec : ConfigShareCodec {
    override val protocol: String = ConnectConfigTypes.CHAIN_LINK

    private const val KEY_SEPARATOR = ':'.code
    private const val DEFLATE_BUFFER_SIZE = 1024
    private const val MAX_INFLATED_BYTES = 512 * 1024

    // Format: chain-link://base64url(raw-deflate(records)).
    // Record: utf8Key ':' u32leValueByteLength utf8ValueBytes.
    private val PARAM_PATHS = linkedMapOf(
        "n" to "name",
        "su" to "subUrl",
        "eu" to "exitUri",

        "sui" to "settings.subUpdateInterval",
        "ft" to "settings.fetchTimeout",
        "mf" to "settings.maxFronts",
        "st" to "settings.strict",
        "obs" to "settings.observer",
        "pu" to "settings.probeUrl",
        "cu" to "settings.connectivityUrl",
        "pi" to "settings.probeInterval",
        "pt" to "settings.probeTimeout",
        "ps" to "settings.probeSampling",
        "pm" to "settings.probeMethod",
        "bs" to "settings.balancerStrategy",
        "ex" to "settings.expected",
        "mr" to "settings.maxRtt",
        "bl" to "settings.baseline",

        "li.l" to "settings.localInbounds.listen",
        "li.in" to "settings.localInbounds.inbounds",
        "li.sp" to "settings.localInbounds.socksPort",
        "li.hp" to "settings.localInbounds.httpPort",
        "li.ip" to "settings.localInbounds.internalPort",

        "rd.ds" to "settings.routingDns.domainStrategy",
        "rd.dns" to "settings.routingDns.dnsServers",
        "rd.dd" to "settings.routingDns.domesticDnsServers",
        "rd.v4" to "settings.routingDns.useIpv4",
        "rd.bp" to "settings.routingDns.bypassPrivate",
        "rd.bu" to "settings.routingDns.blockUdp443",
        "rd.sr" to "settings.routingDns.sniffRouteOnly",

        "rt.xb" to "settings.runtime.xrayBin",
        "rt.ll" to "settings.runtime.loglevel",
        "rt.nr" to "settings.runtime.noRun",
        "rt.o" to "settings.runtime.once",
        "rt.pc" to "settings.runtime.printConfig",
    )
    private val BOOLEAN_PATHS = setOf(
        "settings.strict",
        "settings.routingDns.useIpv4",
        "settings.routingDns.bypassPrivate",
        "settings.routingDns.blockUdp443",
        "settings.routingDns.sniffRouteOnly",
        "settings.runtime.noRun",
        "settings.runtime.once",
        "settings.runtime.printConfig",
    )

    override fun encode(config: ConnectConfigContainer): String? {
        val chainLink = ChainLinkConfigDataMapper.fromContainer(config) ?: return null
        val records = ByteArrayOutputStream()
        PARAM_PATHS.forEach { (key, path) ->
            valueAtPath(chainLink, path)?.let { value ->
                records.writeRecord(key, value)
            }
        }

        val data = Base64.encodeToString(
            deflate(records.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        return "$protocol://$data"
    }

    override fun decode(link: String): DecodedConfigDraft? {
        val normalizedLink = link.trim()
        val prefix = "$protocol://"
        if (!normalizedLink.startsWith(prefix)) {
            return null
        }

        val payload = normalizedLink.removePrefix(prefix)
        if (payload.isBlank()) {
            return null
        }

        val records = try {
            parseRecords(inflate(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: DataFormatException) {
            return null
        }

        val name = records["n"]?.takeIf { it.isNotBlank() } ?: return null
        val subUrl = records["su"]?.takeIf { it.isNotBlank() } ?: return null
        val exitUri = records["eu"]?.takeIf { it.isNotBlank() }.orEmpty()
        val settings = JSONObject()

        records.forEach { (key, value) ->
            val path = PARAM_PATHS[key] ?: return@forEach
            if (!path.startsWith("settings.")) {
                return@forEach
            }
            putJsonPath(settings, path.removePrefix("settings.").split("."), value, path)
                ?: return null
        }

        return DecodedChainLinkDraft(
            chainLink = ChainLinkDraft(
                name = name,
                subUrl = subUrl,
                exitUri = exitUri,
                settings = settings,
            )
        )
    }

    private fun valueAtPath(config: ChainLinkConfigData, path: String): String? {
        return when (path) {
            "name" -> config.name
            "subUrl" -> config.subUrl
            "exitUri" -> config.exitUri.takeIf { it.isNotBlank() }
            else -> valueAtJsonPath(config.settings, path.removePrefix("settings.").split("."))
        }
    }

    private fun valueAtJsonPath(json: JSONObject, path: List<String>): String? {
        var current = json
        path.dropLast(1).forEach { key ->
            current = current.optJSONObject(key) ?: return null
        }

        val leafKey = path.lastOrNull() ?: return null
        if (!current.has(leafKey)) {
            return null
        }

        return when (val value = current.opt(leafKey)) {
            is Boolean -> if (value) "1" else "0"
            JSONObject.NULL, null -> null
            else -> value.toString()
        }
    }

    private fun putJsonPath(root: JSONObject, path: List<String>, value: String, fullPath: String): JSONObject? {
        var current = root
        path.dropLast(1).forEach { key ->
            val next = current.optJSONObject(key) ?: JSONObject().also { current.put(key, it) }
            current = next
        }

        val leafKey = path.lastOrNull() ?: return null
        val decodedValue = if (fullPath in BOOLEAN_PATHS) {
            when (value.lowercase()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> return null
            }
        } else {
            value
        }
        current.put(leafKey, decodedValue)
        return root
    }

    private fun ByteArrayOutputStream.writeRecord(key: String, value: String) {
        require(KEY_SEPARATOR.toChar() !in key) {
            "Chain-link share key cannot contain ':'"
        }

        val valueBytes = value.toByteArray(Charsets.UTF_8)
        write(key.toByteArray(Charsets.UTF_8))
        write(KEY_SEPARATOR)
        write(valueBytes.size and 0xff)
        write((valueBytes.size shr 8) and 0xff)
        write((valueBytes.size shr 16) and 0xff)
        write((valueBytes.size shr 24) and 0xff)
        write(valueBytes)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFLATE_BUFFER_SIZE)
        try {
            deflater.setInput(data)
            deflater.finish()
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFLATE_BUFFER_SIZE)
        try {
            inflater.setInput(data)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw DataFormatException("Incomplete chain-link payload")
                    }
                    throw DataFormatException("Malformed chain-link payload")
                } else {
                    output.write(buffer, 0, count)
                    if (output.size() > MAX_INFLATED_BYTES) {
                        throw DataFormatException("Chain-link payload is too large")
                    }
                }
            }
            return output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun parseRecords(bytes: ByteArray): Map<String, String> {
        val records = linkedMapOf<String, String>()
        var offset = 0

        while (offset < bytes.size) {
            val keyStart = offset
            while (offset < bytes.size && bytes[offset].toInt() != KEY_SEPARATOR) {
                offset += 1
            }
            if (offset == bytes.size || offset == keyStart) {
                throw IllegalArgumentException("Malformed chain-link record key")
            }

            val key = bytes.decodeToString(keyStart, offset)
            offset += 1

            if (offset + 4 > bytes.size) {
                throw IllegalArgumentException("Malformed chain-link record length")
            }
            val valueLength = bytes.u32LeAt(offset)
            offset += 4

            if (valueLength < 0 || offset + valueLength > bytes.size) {
                throw IllegalArgumentException("Malformed chain-link record value")
            }

            records[key] = bytes.decodeToString(offset, offset + valueLength)
            offset += valueLength
        }

        return records
    }

    private fun ByteArray.u32LeAt(offset: Int): Int {
        val value = (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
        if (value < 0) {
            throw IllegalArgumentException("Chain-link value is too large")
        }
        return value
    }
}
