package app.otium.data.backup.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeyTest {

    /** The cheapest settings the app accepts, to keep the tests fast. */
    private val params = KdfParams(memoryKib = 8 * 1024, iterations = 1, parallelism = 1)
    private val salt = ByteArray(BackupKey.SALT_SIZE) { it.toByte() }

    private fun derive(passphrase: String, salt: ByteArray = this.salt) =
        BackupKey.derive(passphrase.toCharArray(), salt, params)

    @Test
    fun `the same passphrase, salt and settings give the same key`() {
        val first = derive("correct horse")
        val second = derive("correct horse")

        assertArrayEquals(first.bytes, second.bytes)
        assertEquals(first.id, second.id)
    }

    @Test
    fun `a different passphrase gives a different key`() {
        assertNotEquals(derive("correct horse").id, derive("correct house").id)
    }

    @Test
    fun `a different salt gives a different key`() {
        val otherSalt = ByteArray(BackupKey.SALT_SIZE) { (it + 1).toByte() }

        assertNotEquals(derive("correct horse").id, derive("correct horse", otherSalt).id)
    }

    @Test
    fun `a passphrase outside ASCII derives the same key every time`() {
        assertEquals(derive("ëndërr çaj 📚").id, derive("ëndërr çaj 📚").id)
    }

    @Test
    fun `new keys get a fresh salt`() {
        val first = BackupKey.create("correct horse".toCharArray(), params)
        val second = BackupKey.create("correct horse".toCharArray(), params)

        assertFalse(first.salt.contentEquals(second.salt))
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `the key is 256 bits and its id is 16 bytes of hex`() {
        val key = derive("correct horse")

        assertEquals(32, key.bytes.size)
        assertTrue(key.id.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun `the id does not reveal the key`() {
        val key = derive("correct horse")

        assertFalse(key.bytes.toHex().startsWith(key.id))
    }

    @Test
    fun `the default settings are acceptable`() {
        assertTrue(KdfParams.DEFAULT.isAcceptable)
    }

    @Test
    fun `settings from a damaged or hostile header are refused`() {
        listOf(
            KdfParams(memoryKib = 1024, iterations = 3, parallelism = 1),
            KdfParams(memoryKib = 4 * 1024 * 1024, iterations = 3, parallelism = 1),
            KdfParams(memoryKib = 64 * 1024, iterations = 0, parallelism = 1),
            KdfParams(memoryKib = 64 * 1024, iterations = 1_000, parallelism = 1),
            KdfParams(memoryKib = 64 * 1024, iterations = 3, parallelism = 0),
            KdfParams(memoryKib = 64 * 1024, iterations = 3, parallelism = 64),
        ).forEach { assertFalse("$it", it.isAcceptable) }
    }

    @Test
    fun `hex round-trips and rejects what is not hex`() {
        val bytes = byteArrayOf(0, 1, 0x7f, -1, -128)

        assertArrayEquals(bytes, bytes.toHex().hexToBytes())
        assertNull("abc".hexToBytes())
        assertNull("zz".hexToBytes())
    }
}
