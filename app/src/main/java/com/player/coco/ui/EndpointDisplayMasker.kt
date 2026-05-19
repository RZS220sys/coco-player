package com.player.coco.ui

object EndpointDisplayMasker {
    fun renderSummary(summary: ConfigItemSummary, censorToken: String): String {
        return summary.parts.joinToString("") { part ->
            if (part.canCensor) {
                maskEndpoint(part.text, censorToken)
            } else {
                part.text
            }
        }
    }

    fun maskEndpoint(endpoint: String, censorToken: String): String {
        if (censorToken.isBlank()) {
            return endpoint
        }

        val trimmed = endpoint.trim()
        if (trimmed.isBlank()) {
            return endpoint
        }

        val separator = Regex("""\s+:\s+""").find(trimmed)
        if (separator != null) {
            val host = trimmed.substring(0, separator.range.first).trim()
            val suffix = trimmed.substring(separator.range.first)
            return maskHost(host, censorToken) + suffix
        }

        return maskHost(trimmed, censorToken)
    }

    fun maskHost(host: String, censorToken: String): String {
        if (censorToken.isBlank()) {
            return host
        }

        val chars = host.codePointsList()
        if (chars.size <= VISIBLE_EDGE_CHARS * 2) {
            return host
        }

        val prefix = chars.take(VISIBLE_EDGE_CHARS).joinToString("")
        val suffix = chars.takeLast(VISIBLE_EDGE_CHARS).joinToString("")
        val hiddenCount = (chars.size - (VISIBLE_EDGE_CHARS * 2)).coerceAtMost(MAX_CENSOR_TOKENS)
        return prefix + censorToken.repeat(hiddenCount) + suffix
    }

    private fun String.codePointsList(): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            result.add(String(Character.toChars(codePoint)))
            index += Character.charCount(codePoint)
        }
        return result
    }

    private const val VISIBLE_EDGE_CHARS = 3
    private const val MAX_CENSOR_TOKENS = 3
}
