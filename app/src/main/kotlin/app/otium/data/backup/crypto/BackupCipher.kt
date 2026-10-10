package app.otium.data.backup.crypto

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.util.SecretBytes
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException

private const val BUFFER_BYTES = 64 * 1024
private const val DEFAULT_SEGMENT_BYTES = 256 * 1024
private const val TINK_KEY_BYTES = 32
private const val VERSION: Byte = 1
private const val KDF_ARGON2ID: Byte = 1
private val MAGIC = "OTIUMENC".toByteArray(Charsets.US_ASCII)

/**
 * The plain header at the start of an encrypted backup. It holds what is needed to derive the key
 * again from the passphrase, and the id of that key so a wrong passphrase shows before anything is
 * decrypted. It is not secret, but it is authenticated: the encryption binds it as associated
 * data, so a changed header makes the backup fail to open.
 *
 * Layout, big-endian: `OTIUMENC`, version (1 byte), KDF (1 byte, 1 = Argon2id v1.3), memory in
 * KiB, iterations and parallelism (4 bytes each), salt (16 bytes), key id (16 bytes).
 */
internal class EncryptedHeader(val params: KdfParams, val salt: ByteArray, val keyId: String) {

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC)
            out.writeByte(VERSION.toInt())
            out.writeByte(KDF_ARGON2ID.toInt())
            out.writeInt(params.memoryKib)
            out.writeInt(params.iterations)
            out.writeInt(params.parallelism)
            out.write(salt)
            out.write(checkNotNull(keyId.hexToBytes()) { "Key id is not hex" })
        }
        return bytes.toByteArray()
    }

    companion object {
        /**
         * The header at the start of [input], or null when it is not an encrypted backup this
         * version can read. Reads exactly the header, leaving [input] at the encrypted data.
         */
        @Suppress("SwallowedException") // A file too short for a header is simply not one.
        fun read(input: InputStream): EncryptedHeader? = try {
            val data = DataInputStream(input)
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            val known = magic.contentEquals(MAGIC) &&
                data.readByte() == VERSION &&
                data.readByte() == KDF_ARGON2ID
            if (known) {
                val params = KdfParams(data.readInt(), data.readInt(), data.readInt())
                val salt = ByteArray(BackupKey.SALT_SIZE).also(data::readFully)
                val keyId = ByteArray(BackupKey.KEY_ID_SIZE).also(data::readFully).toHex()
                EncryptedHeader(params, salt, keyId)
            } else {
                null
            }
        } catch (error: EOFException) {
            null
        }
    }
}

/**
 * Encrypts and decrypts backup files with Tink's streaming AES-256-GCM (HKDF-SHA256 per file,
 * authenticated in segments), so a backup of any size is handled without holding it in memory. A
 * changed, reordered or cut-off segment fails decryption.
 */
internal class BackupCipher(private val segmentBytes: Int = DEFAULT_SEGMENT_BYTES) {

    /** Writes [plain] to [target], encrypted under [key]. */
    @Throws(IOException::class, GeneralSecurityException::class)
    fun encrypt(plain: File, target: File, key: BackupKey) {
        val header = EncryptedHeader(key.params, key.salt, key.id).encode()
        target.outputStream().buffered().use { out ->
            out.write(header)
            streamingAead(key).newEncryptingStream(out, header).use { encrypted ->
                plain.inputStream().use { it.copyTo(encrypted, BUFFER_BYTES) }
            }
        }
    }

    /** The header of [file], or null when it is not an encrypted backup. */
    @Suppress("SwallowedException") // An unreadable file is reported as a damaged backup.
    fun readHeader(file: File): EncryptedHeader? = try {
        file.inputStream().buffered().use(EncryptedHeader::read)
    } catch (error: IOException) {
        null
    }

    /**
     * Writes the plain contents of [source] to [target] using [key]. Throws when the data was
     * changed or cut short; [target] may then hold part of the data and is the caller's to delete.
     */
    @Throws(IOException::class, GeneralSecurityException::class)
    fun decrypt(source: File, target: File, key: BackupKey) {
        source.inputStream().buffered().use { input ->
            val header = EncryptedHeader.read(input) ?: throw IOException("Not an encrypted backup")
            val decrypting = streamingAead(key).newDecryptingStream(input, header.encode())
            decrypting.use { plain ->
                target.outputStream().use { out -> plain.copyTo(out, BUFFER_BYTES) }
            }
        }
    }

    private fun streamingAead(key: BackupKey): StreamingAead {
        val parameters = AesGcmHkdfStreamingParameters.builder()
            .setKeySizeBytes(TINK_KEY_BYTES)
            .setDerivedAesGcmKeySizeBytes(TINK_KEY_BYTES)
            .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
            .setCiphertextSegmentSizeBytes(segmentBytes)
            .build()
        val tinkKey = AesGcmHkdfStreamingKey.create(
            parameters,
            SecretBytes.copyFrom(key.bytes, InsecureSecretKeyAccess.get()),
        )
        return AesGcmHkdfStreaming.create(tinkKey)
    }
}
