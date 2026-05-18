package com.player.coco.data

import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

data class CocoConnectAuth(
    val saltBase64: String,
    val hashBase64: String,
)

class MusicSettingsStore(private val filesDir: File) {
    private val settingsFile = File(filesDir, SETTINGS_FILE_NAME)

    fun load(): JSONObject {
        if (!settingsFile.exists()) {
            return JSONObject()
        }

        return try {
            JSONObject(settingsFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun save(settings: JSONObject) {
        filesDir.mkdirs()
        val atomicFile = AtomicFile(settingsFile)
        val output = atomicFile.startWrite()
        try {
            output.write(settings.toString(2).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun displayedConnectPassDialog(): Boolean {
        return load().optBoolean(KEY_DISPLAYED_CONNECT_PASS_DIALOG, false)
    }

    fun hasCocoConnectAuth(): Boolean {
        return load().optJSONObject(KEY_COCO_CONNECT_AUTH) != null
    }

    fun saveCocoConnectPass(pass: String?) {
        val settings = load()
        settings.put(KEY_DISPLAYED_CONNECT_PASS_DIALOG, true)
        val normalized = pass.orEmpty()
        if (normalized.isBlank()) {
            settings.put(KEY_COCO_CONNECT_AUTH, JSONObject.NULL)
        } else {
            settings.put(KEY_COCO_CONNECT_AUTH, buildAuth(normalized).toJson())
        }
        save(settings)
    }

    fun verifyCocoConnectPass(pass: String): Boolean {
        val auth = cocoConnectAuth() ?: return pass.isBlank()
        val salt = decodeBase64(auth.saltBase64)
        val expectedHash = decodeBase64(auth.hashBase64)
        val actualHash = authHash(salt, pass)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    fun connectKeyFromPass(pass: String): ByteArray? {
        if (!verifyCocoConnectPass(pass)) {
            return null
        }
        return sha256(pass.toByteArray(Charsets.UTF_8))
    }

    private fun cocoConnectAuth(): CocoConnectAuth? {
        val json = load().optJSONObject(KEY_COCO_CONNECT_AUTH) ?: return null
        val salt = json.optString(KEY_SALT)
        val hash = json.optString(KEY_HASH)
        if (salt.isBlank() || hash.isBlank()) {
            return null
        }
        return CocoConnectAuth(saltBase64 = salt, hashBase64 = hash)
    }

    private fun buildAuth(pass: String): CocoConnectAuth {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        return CocoConnectAuth(
            saltBase64 = encodeBase64(salt),
            hashBase64 = encodeBase64(authHash(salt, pass)),
        )
    }

    private fun CocoConnectAuth.toJson(): JSONObject {
        return JSONObject()
            .put(KEY_VERSION, 1)
            .put(KEY_SALT, saltBase64)
            .put(KEY_HASH, hashBase64)
    }

    private fun authHash(salt: ByteArray, pass: String): ByteArray {
        return sha256(salt + pass.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val SETTINGS_FILE_NAME = "music_settings.json"
        private const val KEY_DISPLAYED_CONNECT_PASS_DIALOG = "displayedConnectPassDialog"
        private const val KEY_COCO_CONNECT_AUTH = "cocoConnectAuth"
        private const val KEY_VERSION = "version"
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val SALT_BYTES = 16
        private val secureRandom = SecureRandom()

        fun sha256(input: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(input)
        }

        fun encodeBase64(input: ByteArray): String {
            return Base64.encodeToString(input, Base64.NO_WRAP)
        }

        fun decodeBase64(input: String): ByteArray {
            return Base64.decode(input, Base64.NO_WRAP)
        }
    }
}
