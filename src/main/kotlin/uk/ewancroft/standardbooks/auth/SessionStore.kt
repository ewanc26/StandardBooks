package uk.ewancroft.standardbooks.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Stores AT Protocol OAuth sessions encrypted at rest.
 *
 * Ported from Inkwell's KeychainStore + socialsync's AtProtoSessionManager patterns.
 * Uses AES-256-GCM for encryption, with a per-installation key derived from a
 * random key file stored in the plugin data folder.
 */
class SessionStore(private val dataFolder: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val sessionsFile = File(dataFolder, "sessions.json")
    private val keyFile = File(dataFolder, ".sessionkey")
    private val sessions: MutableMap<String, Session> = mutableMapOf()

    @Serializable
    data class Session(
        val did: String,
        val handle: String,
        val pdsUrl: String,
        val accessToken: String,
        val refreshToken: String?,
        val dpopKey: ByteArray,
        val createdAt: Long
    )

    fun load() {
        if (!sessionsFile.exists()) return
        try {
            val key = getOrCreateKey()
            val encrypted = sessionsFile.readBytes()
            val decrypted = decrypt(key, encrypted)
            val loaded = json.decodeFromString<Map<String, Session>>(decrypted)
            sessions.clear()
            sessions.putAll(loaded)
        } catch (e: Exception) {
            // Corrupt or incompatible — start fresh
            sessionsFile.delete()
        }
    }

    fun save() {
        val key = getOrCreateKey()
        val plain = json.encodeToString(sessions)
        val encrypted = encrypt(key, plain.toByteArray())
        sessionsFile.writeBytes(encrypted)
    }

    fun get(playerUuid: String): Session? = sessions[playerUuid]

    fun put(playerUuid: String, session: Session) {
        sessions[playerUuid] = session
        save()
    }

    fun remove(playerUuid: String) {
        sessions.remove(playerUuid)
        save()
    }

    fun has(playerUuid: String): Boolean = sessions.containsKey(playerUuid)

    private fun getOrCreateKey(): SecretKey {
        if (keyFile.exists()) {
            return SecretKeySpec(keyFile.readBytes(), "AES")
        }
        val random = SecureRandom()
        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)
        keyFile.writeBytes(keyBytes)
        keyFile.setReadOnly()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(key: SecretKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    private fun decrypt(key: SecretKey, data: ByteArray): String {
        require(data.size > 12) { "Encrypted data too short" }
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }
}
