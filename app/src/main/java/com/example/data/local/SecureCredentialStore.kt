package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.AwsCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureCredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    suspend fun saveCredentials(credentials: AwsCredentials) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS_ID, encryptString(credentials.accessKeyId.trim()))
            .putString(KEY_SECRET_ACCESS, encryptString(credentials.secretAccessKey.trim()))
            .putString(KEY_SESSION_TOKEN, encryptString(credentials.sessionToken.trim()))
            .putString(KEY_REGION, credentials.region.trim().ifBlank { "us-east-1" })
            .apply()
    }

    suspend fun loadCredentials(): AwsCredentials = withContext(Dispatchers.IO) {
        val accessKey = decryptString(prefs.getString(KEY_ACCESS_ID, null))
        val secretKey = decryptString(prefs.getString(KEY_SECRET_ACCESS, null))
        val sessionToken = decryptString(prefs.getString(KEY_SESSION_TOKEN, null))
        val region = prefs.getString(KEY_REGION, "us-east-1") ?: "us-east-1"
        AwsCredentials(
            accessKeyId = accessKey,
            secretAccessKey = secretKey,
            sessionToken = sessionToken,
            region = region
        )
    }

    suspend fun clearCredentials() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_ACCESS_ID)
            .remove(KEY_SECRET_ACCESS)
            .remove(KEY_SESSION_TOKEN)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptString(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse { "" }
    }

    private fun decryptString(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.isEmpty()) return ""
            val ivLen = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivLen) return ""
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrElse { "" }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "voxbound_aws_iam_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "voxbound_secure_creds"
        private const val KEY_ACCESS_ID = "enc_access_id"
        private const val KEY_SECRET_ACCESS = "enc_secret_access"
        private const val KEY_SESSION_TOKEN = "enc_session_token"
        private const val KEY_REGION = "aws_region"
    }
}
