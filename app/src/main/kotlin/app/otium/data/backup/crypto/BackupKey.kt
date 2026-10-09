package app.otium.data.backup.crypto

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

private const val KEY_BYTES = 32
private const val SALT_BYTES = 16
private const val KEY_ID_BYTES = 16
private const val KEY_ID_LABEL = "otium backup key id v1"
private const val HMAC = "HmacSHA256"

/**
 * Argon2id cost settings. Each encrypted backup records the ones it was made with, so they can be
 * raised later without making older backups unreadable.
 */
internal data class KdfParams(val memoryKib: Int, val iterations: Int, val parallelism: Int) {

    /**
     * True for settings this app is willing to run. A backup comes from the user's Drive and is
     * untrusted, so a header asking for gigabytes of memory or endless passes is treated as
     * damaged instead of being obeyed.
     */
    val isAcceptable: Boolean
        get() = memoryKib in MIN_MEMORY_KIB..MAX_MEMORY_KIB &&
            iterations in 1..MAX_ITERATIONS &&
            parallelism in 1..MAX_PARALLELISM

    companion object {
        /** 64 MiB, 3 passes, 1 lane: RFC 9106's second recommended setting, at one lane. */
        val DEFAULT = KdfParams(memoryKib = 64 * 1024, iterations = 3, parallelism = 1)

        private const val MIN_MEMORY_KIB = 8 * 1024
        private const val MAX_MEMORY_KIB = 256 * 1024
        private const val MAX_ITERATIONS = 10
        private const val MAX_PARALLELISM = 4
    }
}

/**
 * A backup key derived from a passphrase, with what is needed to derive it again: the salt and
 * the Argon2id settings. [id] names the key without revealing it, so a backup can say which key
 * opens it and a wrong passphrase is caught before any decryption is tried.
 */
internal class BackupKey(val bytes: ByteArray, val salt: ByteArray, val params: KdfParams) {

    val id: String = keyIdOf(bytes)

    companion object {
        /** A key for [passphrase] with a fresh random salt. */
        fun create(
            passphrase: CharArray,
            params: KdfParams = KdfParams.DEFAULT,
            random: SecureRandom = SecureRandom(),
        ): BackupKey {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            return derive(passphrase, salt, params)
        }

        /** The key [passphrase] gives with [salt] and [params]; slow on purpose. */
        fun derive(passphrase: CharArray, salt: ByteArray, params: KdfParams): BackupKey {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKib)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
            val key = ByteArray(KEY_BYTES)
            Argon2BytesGenerator().apply { init(argon) }.generateBytes(passphrase, key)
            return BackupKey(key, salt.copyOf(), params)
        }

        /** HMAC-SHA256 of a fixed label under the key, cut to 16 bytes, as hex. */
        fun keyIdOf(key: ByteArray): String {
            val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }
            return mac.doFinal(KEY_ID_LABEL.toByteArray(Charsets.UTF_8))
                .copyOf(KEY_ID_BYTES)
                .toHex()
        }

        const val SALT_SIZE = SALT_BYTES
        const val KEY_ID_SIZE = KEY_ID_BYTES
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toIntOrNull(radix = 16)?.toByte() ?: return null
    }
}
