package app.otium.data.backup.crypto

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupCipherTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Small segments, so a few kilobytes already span several of them. */
    private val cipher = BackupCipher(segmentBytes = 1024)
    private val params = KdfParams(memoryKib = 8 * 1024, iterations = 1, parallelism = 1)
    private val key = BackupKey.create("correct horse".toCharArray(), params)

    private val content = ByteArray(10_000).also(Random(7)::nextBytes)
    private val plain by lazy { folder.newFile("plain.zip").apply { writeBytes(content) } }

    private fun encrypted(): File = folder.newFile().also { cipher.encrypt(plain, it, key) }

    private fun decrypt(source: File, with: BackupKey = key): ByteArray {
        val target = folder.newFile()
        cipher.decrypt(source, target, with)
        return target.readBytes()
    }

    private fun assertRejected(source: File, with: BackupKey = key) {
        try {
            decrypt(source, with)
            fail("A changed backup must not decrypt")
        } catch (expected: GeneralSecurityException) {
            // Rejected by the authentication tag.
        } catch (expected: IOException) {
            // Rejected while streaming.
        }
    }

    private fun File.flipByteAt(index: Int) {
        val bytes = readBytes()
        bytes[index] = (bytes[index].toInt() xor 1).toByte()
        writeBytes(bytes)
    }

    @Test
    fun `a backup decrypts back to exactly what was encrypted`() {
        assertArrayEquals(content, decrypt(encrypted()))
    }

    @Test
    fun `an empty file round-trips`() {
        val empty = folder.newFile("empty.zip")
        val sealed = folder.newFile().also { cipher.encrypt(empty, it, key) }

        assertEquals(0, decrypt(sealed).size)
    }

    @Test
    fun `the encrypted file does not contain the plain bytes`() {
        val sealed = encrypted().readBytes()

        val sample = content.copyOfRange(100, 132)
        val found = (0..sealed.size - sample.size).any { start ->
            sample.indices.all { sealed[start + it] == sample[it] }
        }
        assertFalse(found)
    }

    @Test
    fun `the header names the key and its settings`() {
        val header = checkNotNull(cipher.readHeader(encrypted()))

        assertEquals(key.id, header.keyId)
        assertEquals(params, header.params)
        assertArrayEquals(key.salt, header.salt)
    }

    @Test
    fun `a plain zip has no header`() {
        val zip = folder.newFile("backup.zip").apply { writeBytes(byteArrayOf(0x50, 0x4b, 3, 4)) }

        assertNull(cipher.readHeader(zip))
    }

    @Test
    fun `a file shorter than a header has none`() {
        val stub = folder.newFile().apply { writeBytes("OTIUMENC".toByteArray()) }

        assertNull(cipher.readHeader(stub))
    }

    @Test
    fun `a changed byte in the data is rejected`() {
        val sealed = encrypted()
        sealed.flipByteAt(sealed.length().toInt() / 2)

        assertRejected(sealed)
    }

    @Test
    fun `a cut-off backup is rejected`() {
        val sealed = encrypted()
        sealed.writeBytes(sealed.readBytes().copyOf(sealed.length().toInt() - 1500))

        assertRejected(sealed)
    }

    @Test
    fun `a changed header is rejected because it is authenticated`() {
        val sealed = encrypted()
        // The last header byte belongs to the key id; the data after it is untouched.
        val headerSize = EncryptedHeader(params, key.salt, key.id).encode().size
        sealed.flipByteAt(headerSize - 1)

        assertRejected(sealed)
    }

    @Test
    fun `another key does not open it`() {
        val other = BackupKey.create("battery staple".toCharArray(), params)

        assertRejected(encrypted(), other)
    }

    @Test
    fun `a backup that decrypts past the size limit is rejected`() {
        val capped = BackupCipher(segmentBytes = 1024, maxPlainBytes = 4_096)
        val sealed = encrypted()
        val target = folder.newFile()

        try {
            capped.decrypt(sealed, target, key)
            fail("A backup larger than maxPlainBytes must not decrypt")
        } catch (expected: IOException) {
            // Rejected once the stream passes the cap.
        }
    }
}
