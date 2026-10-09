package app.otium.data.backup.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets for storage on the device, and decrypts them again. */
internal interface KeyWrapper {
    @Throws(GeneralSecurityException::class)
    fun wrap(secret: ByteArray): ByteArray

    @Throws(GeneralSecurityException::class)
    fun unwrap(wrapped: ByteArray): ByteArray

    /** Forgets the wrapping key; anything wrapped with it can no longer be unwrapped. */
    fun delete()
}

private const val PROVIDER = "AndroidKeyStore"
private const val ALIAS = "otium_backup_key_wrapper"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_BITS = 128
private const val KEY_BITS = 256

/**
 * Wraps with an AES-256-GCM key that lives in the Android Keystore and cannot be read out of it,
 * so the stored backup key is useless off this device. It needs no unlock or fingerprint: a
 * scheduled backup runs while the phone may be locked. Wrapped form: IV length, IV, ciphertext.
 */
internal class AndroidKeystoreKeyWrapper : KeyWrapper {

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(createIfMissing = true))
        }
        val iv = cipher.iv
        val sealed = cipher.doFinal(secret)
        return ByteBuffer.allocate(1 + iv.size + sealed.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(sealed)
            .array()
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val ivSize = wrapped.firstOrNull()?.toInt() ?: 0
        if (ivSize <= 0 || wrapped.size <= 1 + ivSize) {
            throw GeneralSecurityException("Not a wrapped key")
        }
        val buffer = ByteBuffer.wrap(wrapped, 1, wrapped.size - 1)
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val sealed = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(createIfMissing = false), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(sealed)
    }

    override fun delete() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun key(createIfMissing: Boolean): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!createIfMissing) throw GeneralSecurityException("The backup key wrapper is gone")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
