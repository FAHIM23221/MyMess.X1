package com.smarttransparentmess.erp.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores Supabase refresh/access tokens and the temporary PKCE verifier encrypted with
 * an AES-GCM key held by Android Keystore. The app disables Android backup so the
 * encrypted preferences cannot be restored without their Keystore key.
 */
class AndroidEncryptedAuthStorage(context: Context) : SessionManager, CodeVerifierCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        writeEncrypted(SESSION_KEY, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        val value = readEncrypted(SESSION_KEY) ?: throw NoSessionFoundException()
        return runCatching { json.decodeFromString(UserSession.serializer(), value) }
            .getOrElse {
                deleteValue(SESSION_KEY)
                throw NoSessionFoundException()
            }
    }

    override suspend fun deleteSession() {
        deleteValue(SESSION_KEY)
    }

    override suspend fun saveCodeVerifier(codeVerifier: String) {
        writeEncrypted(PKCE_KEY, codeVerifier)
    }

    override suspend fun loadCodeVerifier(): String? = readEncrypted(PKCE_KEY)

    override suspend fun deleteCodeVerifier() {
        deleteValue(PKCE_KEY)
    }

    private suspend fun writeEncrypted(name: String, plainText: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        if (!preferences.edit().putString(name, encoded).commit()) {
            throw IOException("Could not persist encrypted authentication data")
        }
    }

    private suspend fun readEncrypted(name: String): String? = withContext(Dispatchers.IO) {
        val encoded = preferences.getString(name, null) ?: return@withContext null
        try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) throw IllegalArgumentException("Invalid encrypted payload")
            val buffer = ByteBuffer.wrap(payload)
            val iv = ByteArray(GCM_IV_BYTES)
            buffer.get(iv)
            val cipherText = ByteArray(buffer.remaining())
            buffer.get(cipherText)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            // A restored/corrupt token must never be treated as an authenticated session.
            preferences.edit().remove(name).commit()
            null
        }
    }

    private suspend fun deleteValue(name: String) = withContext(Dispatchers.IO) {
        preferences.edit().remove(name).commit()
        Unit
    }

    private fun getOrCreateKey(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return@synchronized it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "smart_mess_auth_secure"
        const val SESSION_KEY = "supabase_session_aes_gcm"
        const val PKCE_KEY = "supabase_pkce_verifier_aes_gcm"
        const val KEY_ALIAS = "smart_mess_supabase_auth_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
