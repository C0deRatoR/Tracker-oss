package com.yash.tracker.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the Gemini API key at rest.
 *
 * TRD §9 specifies EncryptedSharedPreferences, but androidx.security-crypto has had every
 * API deprecated and has shipped nothing since July 2025. Google's replacement guidance is
 * to use the Android Keystore directly, which is what this does: a 256-bit AES key that is
 * generated inside the Keystore and never leaves it, used to seal the value that DataStore
 * then holds as ciphertext.
 */
@Singleton
class SecureKeyStore @Inject constructor() {

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "${ciphertext.encode()}$SEPARATOR${cipher.iv.encode()}"
    }

    /** Returns null when the stored value cannot be read back — a wiped Keystore, say. */
    fun decrypt(stored: String): String? = runCatching {
        val (ciphertext, iv) = stored.split(SEPARATOR).let { it[0].decode() to it[1].decode() }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "tracker_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = ":"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
