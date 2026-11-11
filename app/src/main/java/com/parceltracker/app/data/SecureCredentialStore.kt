package com.parceltracker.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Gmail app password encrypted with a key held in the Android Keystore.
 * The raw password never touches disk.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("parcel_secure_credentials", Context.MODE_PRIVATE)

    @Synchronized
    fun saveAppPassword(email: String, password: String) {
        val encrypted = encrypt(password)
        prefs.edit().putString(keyFor(email), encrypted).apply()
    }

    @Synchronized
    fun getAppPassword(email: String): String? {
        val stored = prefs.getString(keyFor(email), null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    @Synchronized
    fun removeAppPassword(email: String) {
        prefs.edit().remove(keyFor(email)).apply()
    }

    /** Moves the single-account password from older builds to its account key. */
    @Synchronized
    fun migrateLegacy(email: String) {
        val legacy = prefs.getString(KEY_APP_PASSWORD, null) ?: return
        if (!prefs.contains(keyFor(email))) {
            runCatching { decrypt(legacy) }.getOrNull()
                ?.let { saveAppPassword(email, it) }
        }
        prefs.edit().remove(KEY_APP_PASSWORD).apply()
    }

    @Synchronized
    fun clearAllAppPasswords() {
        prefs.edit().clear().apply()
    }

    private fun keyFor(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase(Locale.ENGLISH).toByteArray(Charsets.UTF_8))
        val suffix = digest.joinToString("") { "%02x".format(it) }.take(32)
        return KEY_APP_PASSWORD_PREFIX + suffix
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv),
        )
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "parcel_gmail_app_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_APP_PASSWORD = "gmail_app_password"
        private const val KEY_APP_PASSWORD_PREFIX = "gmail_app_password_"
    }
}
