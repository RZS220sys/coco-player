package com.player.coco.logging

import com.player.coco.data.GlobalSettingsStore

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CocoLog {
    private const val LOG_FILE_NAME = "coco.log"
    private const val XRAY_ACCESS_LOG_FILE_NAME = "xray-access.log"
    private const val LEGACY_XRAY_ERROR_LOG_FILE_NAME = "xray-error.log"
    private const val SETTINGS_NAME = "coco_log_settings"
    private const val KEY_FILTER_LEVEL = "filterLevel"
    const val LEVEL_ERROR = "error"
    const val LEVEL_WARNING = "warning"
    const val LEVEL_INFO = "info"
    const val LEVEL_DEBUG = "debug"
    const val MAX_VIEW_LINES = 2500
    private const val DEFAULT_MAX_LOG_MB = 5.0
    private const val MAX_VIEW_BYTES_PER_FILE = 512 * 1024
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private const val LOGCAT_TAGS = "GoLog"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val LEVEL_ORDER = listOf(LEVEL_ERROR, LEVEL_WARNING, LEVEL_INFO, LEVEL_DEBUG)
    private val XRAY_LEVELS = setOf(LEVEL_ERROR, LEVEL_WARNING, LEVEL_INFO, LEVEL_DEBUG, "none")
    private val SUPPRESSED_XRAY_LOG_PATTERNS = arrayOf(
        "[Error] transport/internet/websocket: failed to dial to ",
        "[Warning] app/observatory/burst: error ping",
        "[Warning] common/errors: The feature ",
        "[Warning] infra/conf: \"allowInsecure\" will be removed automatically",
        "[Warning] app/observatory/burst: network is down",
    )

    @Synchronized
    fun debug(context: Context, tag: String, message: String) {
        append(context, LEVEL_DEBUG, "D", tag, message)
    }

    @Synchronized
    fun info(context: Context, tag: String, message: String) {
        append(context, LEVEL_INFO, "I", tag, message)
    }

    @Synchronized
    fun warning(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        append(context, LEVEL_WARNING, "W", tag, messageWithThrowable(message, throwable))
    }

    @Synchronized
    fun error(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        append(context, LEVEL_ERROR, "E", tag, messageWithThrowable(message, throwable))
    }

    @Synchronized
    fun read(context: Context): String {
        return readRecentLines(context, MAX_VIEW_LINES).joinToString(separator = "\n")
    }

    @Synchronized
    fun readRecentLines(context: Context, maxLines: Int = MAX_VIEW_LINES): List<String> {
        trimToBudget(context)
        val files = buildList {
            add("app" to logFile(context))
            if (xrayAccessLogsEnabled(context)) {
                add("xray-access" to xrayAccessLogFile(context))
            }
        }

        val sourceCount = files.size + 1
        val perSourceLimit = (maxLines / sourceCount).coerceAtLeast(200)
        return buildList {
            files.forEach { (source, file) ->
                addAll(readTailLines(source, file, perSourceLimit))
            }
            addAll(readLogcatLines(context, perSourceLimit))
        }
            .takeLast(maxLines)
    }

    @Synchronized
    fun clear(context: Context) {
        logFile(context).delete()
        xrayAccessLogFile(context).delete()
        legacyXrayErrorLogFile(context).delete()
        clearLogcat()
    }

    fun displayFilterLevel(context: Context): String {
        val value = preferences(context).getString(KEY_FILTER_LEVEL, LEVEL_WARNING).orEmpty()
        return value.takeIf { it in LEVEL_ORDER } ?: LEVEL_WARNING
    }

    fun setDisplayFilterLevel(context: Context, level: String) {
        preferences(context)
            .edit()
            .putString(KEY_FILTER_LEVEL, level.takeIf { it in LEVEL_ORDER } ?: LEVEL_WARNING)
            .apply()
    }

    fun xrayLogLevel(context: Context): String {
        val value = logsSettings(context).optString("xrayLogLevel", LEVEL_WARNING)
        return value.takeIf { it in XRAY_LEVELS } ?: LEVEL_WARNING
    }

    fun xrayAccessLogsEnabled(context: Context): Boolean {
        return logsSettings(context).optBoolean("accessLogsEnabled", false)
    }

    fun suppressNoisyXrayLogsEnabled(context: Context): Boolean {
        return logsSettings(context).optBoolean("suppressNoisyLogs", true)
    }

    fun maxLogBytes(context: Context): Long {
        val mb = logsSettings(context).optString("autoTrimMb", DEFAULT_MAX_LOG_MB.toString())
            .toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: DEFAULT_MAX_LOG_MB
        return (mb * 1024.0 * 1024.0).toLong().coerceAtLeast(256L * 1024L)
    }

    fun xrayAccessLogPath(context: Context): String {
        return xrayAccessLogFile(context).absolutePath
    }

    @Synchronized
    fun trimToBudget(context: Context) {
        legacyXrayErrorLogFile(context).delete()
        val files = logFiles(context).filter { it.exists() }
        if (files.isEmpty()) {
            return
        }

        val perFileBudget = (maxLogBytes(context) / files.size).coerceAtLeast(64L * 1024L)
        files.forEach { trimFileToBytes(it, perFileBudget) }
    }

    private fun append(context: Context, level: String, marker: String, tag: String, message: String) {
        if (!shouldLog(context, level)) {
            return
        }

        val file = logFile(context)
        file.parentFile?.mkdirs()
        trimToBudget(context)
        val timestamp = dateFormat.format(Date())
        val normalized = message.lines().joinToString(separator = "\n") { line ->
            "$timestamp $marker/$tag: $line"
        }
        file.appendText("$normalized\n", Charsets.UTF_8)
    }

    private fun shouldLog(context: Context, level: String): Boolean {
        val activeLevel = xrayLogLevel(context)
        if (activeLevel == "none") {
            return false
        }
        val activeIndex = LEVEL_ORDER.indexOf(activeLevel).takeIf { it >= 0 } ?: LEVEL_ORDER.indexOf(LEVEL_WARNING)
        val messageIndex = LEVEL_ORDER.indexOf(level).takeIf { it >= 0 } ?: LEVEL_ORDER.indexOf(LEVEL_ERROR)
        return messageIndex <= activeIndex
    }

    private fun messageWithThrowable(message: String, throwable: Throwable?): String {
        return buildString {
            append(message)
            throwable?.let {
                append('\n')
                append(it.stackTraceToString())
            }
        }
    }

    private fun trimFileToBytes(file: File, maxBytes: Long) {
        if (!file.exists() || file.length() <= maxBytes) {
            return
        }

        val parent = file.parentFile ?: return
        val temp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            RandomAccessFile(file, "r").use { input ->
                input.seek((input.length() - maxBytes).coerceAtLeast(0L))
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
            temp.delete()
        }
    }

    private fun logFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    private fun xrayAccessLogFile(context: Context): File {
        return File(context.filesDir, XRAY_ACCESS_LOG_FILE_NAME)
    }

    private fun legacyXrayErrorLogFile(context: Context): File {
        return File(context.filesDir, LEGACY_XRAY_ERROR_LOG_FILE_NAME)
    }

    private fun logFiles(context: Context): List<File> {
        return listOf(logFile(context), xrayAccessLogFile(context))
    }

    private fun readTailLines(source: String, file: File, maxLines: Int): List<String> {
        if (!file.exists()) {
            return emptyList()
        }

        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val start = (input.length() - MAX_VIEW_BYTES_PER_FILE).coerceAtLeast(0L)
                val size = (input.length() - start).toInt()
                val buffer = ByteArray(size)
                input.seek(start)
                input.readFully(buffer)
                buffer.toString(Charsets.UTF_8)
                    .lines()
                    .filter { it.isNotBlank() }
                    .takeLast(maxLines)
                    .map { line -> "[$source] $line" }
            }
        }.getOrDefault(emptyList())
    }

    private fun readLogcatLines(context: Context, maxLines: Int): List<String> {
        return runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "-s", LOGCAT_TAGS)
                .redirectErrorStream(true)
                .start()
            val suppressNoisy = suppressNoisyXrayLogsEnabled(context)
            val lines = process.inputStream.bufferedReader().useLines { lines ->
                lines
                    .filter { it.isNotBlank() }
                    .filter { !suppressNoisy || !isNoisyXrayLine(it) }
                    .map { "[xray] $it" }
                    .takeLastCompat(maxLines)
            }
            process.waitFor()
            lines
        }.getOrDefault(emptyList())
    }

    private fun clearLogcat() {
        runCatching {
            ProcessBuilder("logcat", "-c")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun isNoisyXrayLine(line: String): Boolean {
        return SUPPRESSED_XRAY_LOG_PATTERNS.any { it in line }
    }

    private fun Sequence<String>.takeLastCompat(count: Int): List<String> {
        if (count <= 0) {
            return emptyList()
        }
        val buffer = ArrayDeque<String>()
        forEach { line ->
            if (buffer.size == count) {
                buffer.removeFirst()
            }
            buffer.addLast(line)
        }
        return buffer.toList()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)

    private fun logsSettings(context: Context) = GlobalSettingsStore(context.filesDir)
        .load()
        .optJSONObject("logs")
        ?: org.json.JSONObject()

}
