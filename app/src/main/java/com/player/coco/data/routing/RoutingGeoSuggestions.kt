package com.player.coco.data.routing

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Locale

data class RoutingGeoTags(
    val domainTags: List<String>,
    val ipTags: List<String>,
)

object RoutingGeoSuggestions {
    fun loadFromAssets(context: Context): RoutingGeoTags {
        return RoutingGeoTags(
            domainTags = readCodes(context, GEOSITE_FILE).map { "geosite:$it" },
            ipTags = readCodes(context, GEOIP_FILE).map { "geoip:$it" },
        )
    }

    private fun readCodes(context: Context, assetName: String): List<String> {
        return runCatching {
            context.assets.open(assetName).use { input ->
                parseTopLevelCodes(input.readBytes())
            }
        }.getOrDefault(emptyList())
    }

    private fun parseTopLevelCodes(data: ByteArray): List<String> {
        val reader = ProtoReader(data)
        val codes = linkedSetOf<String>()
        while (!reader.isAtEnd()) {
            val key = reader.readVarint() ?: break
            val fieldNumber = (key ushr 3).toInt()
            val wireType = (key and 0x7L).toInt()
            if (fieldNumber == ENTRY_FIELD_NUMBER && wireType == WIRE_LENGTH_DELIMITED) {
                val entryReader = reader.readLengthDelimitedReader() ?: break
                parseEntryCode(entryReader)?.let { codes.add(it) }
            } else if (!reader.skip(wireType)) {
                break
            }
        }
        return codes.toList().sorted()
    }

    private fun parseEntryCode(reader: ProtoReader): String? {
        while (!reader.isAtEnd()) {
            val key = reader.readVarint() ?: return null
            val fieldNumber = (key ushr 3).toInt()
            val wireType = (key and 0x7L).toInt()
            if (fieldNumber == COUNTRY_CODE_FIELD_NUMBER && wireType == WIRE_LENGTH_DELIMITED) {
                val code = reader.readLengthDelimitedString()
                    ?.trim()
                    ?.lowercase(Locale.US)
                if (!code.isNullOrBlank() && code.all(::isGeoCodeChar)) {
                    return code
                }
            } else if (!reader.skip(wireType)) {
                return null
            }
        }
        return null
    }

    private fun isGeoCodeChar(char: Char): Boolean {
        return char in 'a'..'z' ||
            char in '0'..'9' ||
            char == '-' ||
            char == '_' ||
            char == '!'
    }

    private class ProtoReader(
        private val data: ByteArray,
        private var position: Int = 0,
        private val limit: Int = data.size,
    ) {
        fun isAtEnd(): Boolean = position >= limit

        fun readVarint(): Long? {
            var shift = 0
            var result = 0L
            while (shift < 64 && position < limit) {
                val byte = data[position++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if ((byte and 0x80) == 0) {
                    return result
                }
                shift += 7
            }
            return null
        }

        fun readLengthDelimitedReader(): ProtoReader? {
            val length = readVarint()?.toInt() ?: return null
            if (length < 0 || position + length > limit) {
                return null
            }
            val reader = ProtoReader(data, position, position + length)
            position += length
            return reader
        }

        fun readLengthDelimitedString(): String? {
            val length = readVarint()?.toInt() ?: return null
            if (length < 0 || position + length > limit) {
                return null
            }
            val value = String(data, position, length, StandardCharsets.UTF_8)
            position += length
            return value
        }

        fun skip(wireType: Int): Boolean {
            return when (wireType) {
                WIRE_VARINT -> readVarint() != null
                WIRE_64_BIT -> skipBytes(8)
                WIRE_LENGTH_DELIMITED -> {
                    val length = readVarint()?.toInt() ?: return false
                    skipBytes(length)
                }
                WIRE_32_BIT -> skipBytes(4)
                else -> false
            }
        }

        private fun skipBytes(length: Int): Boolean {
            if (length < 0 || position + length > limit) {
                return false
            }
            position += length
            return true
        }
    }

    private const val GEOIP_FILE = "geoip.dat"
    private const val GEOSITE_FILE = "geosite.dat"
    private const val ENTRY_FIELD_NUMBER = 1
    private const val COUNTRY_CODE_FIELD_NUMBER = 1
    private const val WIRE_VARINT = 0
    private const val WIRE_64_BIT = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_32_BIT = 5
}
