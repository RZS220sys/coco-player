package com.player.coco.data

import android.util.AtomicFile
import com.player.coco.data.config.ConnectConfigStore
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ConnectCryptoSession {
    private var keyBytes: ByteArray? = null

    fun unlockWithPass(musicSettingsStore: MusicSettingsStore, pass: String): Boolean {
        val key = musicSettingsStore.connectKeyFromPass(pass) ?: return false
        keyBytes = key
        return true
    }

    fun unlockWithoutAuth() {
        keyBytes = null
    }

    fun keyOrNull(): ByteArray? = keyBytes?.copyOf()
}

object ConnectDataCipher {
    fun isPlaintextJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") && trimmed.endsWith("}")
    }

    fun isEncrypted(text: String): Boolean {
        return text.startsWith(FORMAT_PREFIX)
    }

    fun serializeJson(data: JSONObject, key: ByteArray?): String {
        return if (key == null) {
            data.toString(2)
        } else {
            encryptJson(data, key)
        }
    }

    fun encryptJson(data: JSONObject, key: ByteArray): String {
        val iv = ByteArray(IV_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        return FORMAT_PREFIX +
            MusicSettingsStore.encodeBase64(iv) +
            FORMAT_SEPARATOR +
            MusicSettingsStore.encodeBase64(cipherText)
    }

    fun readJson(text: String, key: ByteArray?): JSONObject? {
        val trimmed = text.trim()
        if (isPlaintextJson(trimmed)) {
            return try {
                JSONObject(trimmed)
            } catch (_: Exception) {
                null
            }
        }
        return decryptJson(trimmed, key)
    }

    fun decryptJson(text: String, key: ByteArray?): JSONObject? {
        if (key == null) {
            return null
        }
        if (!isEncrypted(text)) {
            return null
        }

        return try {
            val parts = text.split(FORMAT_SEPARATOR, limit = 3)
            if (parts.size != 3 || parts[0] != FORMAT_NAME) {
                return null
            }
            val iv = MusicSettingsStore.decodeBase64(parts[1])
            val cipherText = MusicSettingsStore.decodeBase64(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
            JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ALGORITHM = "AES"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val FORMAT_NAME = "coco1"
    private const val FORMAT_SEPARATOR = ":"
    private const val FORMAT_PREFIX = "$FORMAT_NAME$FORMAT_SEPARATOR"
    private val secureRandom = SecureRandom()
}

object ConnectDataMigration {
    fun rewriteConnectData(filesDir: File) {
        val globalSettingsStore = GlobalSettingsStore(filesDir)
        globalSettingsStore.save(globalSettingsStore.load())

        val configStore = ConnectConfigStore(filesDir)
        configStore.loadAll().forEach { config ->
            configStore.saveExisting(config.id, config.type, config.data)
        }
        rewriteGeneratedRuntimeConfig(filesDir)
    }

    private fun rewriteGeneratedRuntimeConfig(filesDir: File) {
        if (!MusicSettingsStore(filesDir).hasCocoConnectAuth()) {
            return
        }
        val key = ConnectCryptoSession.keyOrNull() ?: return
        val file = File(filesDir, ConnectConfigStore.GENERATED_XRAY_CONFIG_NAME)
        if (!file.exists()) {
            return
        }

        val text = file.readText(Charsets.UTF_8)
        if (ConnectDataCipher.isEncrypted(text)) {
            return
        }
        val container = ConnectDataCipher.readJson(text, key) ?: return

        val encrypted = ConnectDataCipher.encryptJson(container, key)
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(encrypted.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}
