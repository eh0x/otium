package app.otium.data.backup.crypto

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.DispatcherProvider
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeystoreBackupEncryptionTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Stands in for the Android Keystore: AES-GCM under a key that lives only in memory. */
    private class FakeKeyWrapper : KeyWrapper {
        private var key: SecretKey? = null
        var wraps = 0
            private set

        override fun wrap(secret: ByteArray): ByteArray {
            wraps++
            val sealing = key ?: KeyGenerator.getInstance("AES").generateKey().also { key = it }
            val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, sealing, GCMParameterSpec(TAG_BITS, iv))
            return iv + cipher.doFinal(secret)
        }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            val sealing = key ?: throw GeneralSecurityException("No wrapping key")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                sealing,
                GCMParameterSpec(TAG_BITS, wrapped.copyOf(IV_BYTES)),
            )
            return cipher.doFinal(wrapped.copyOfRange(IV_BYTES, wrapped.size))
        }

        override fun delete() {
            key = null
        }

        private companion object {
            const val IV_BYTES = 12
            const val TAG_BITS = 128
        }
    }

    private object Direct : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val params = KdfParams(memoryKib = 8 * 1024, iterations = 1, parallelism = 1)
    private val wrapper = FakeKeyWrapper()

    /** A phone: its own key store and wrapper. Another name is another device. */
    private fun device(
        name: String = "phone",
        keyWrapper: KeyWrapper = wrapper,
    ): KeystoreBackupEncryption {
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "$name.preferences_pb")
        }
        val work = File(folder.root, "$name-work")
        return KeystoreBackupEncryption(store, keyWrapper, work, Direct, params)
    }

    private fun newPhone() = device(name = "new-phone", keyWrapper = FakeKeyWrapper())

    private val plain by lazy {
        folder.newFile("backup.zip").apply { writeText("the library, zipped") }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun KeystoreBackupEncryption.sealed(): File =
        checkNotNull((encrypt(plain) as Try.Success).value).file

    private fun File.flipByteFromEnd(offset: Int) {
        val bytes = readBytes()
        val index = bytes.size - offset
        bytes[index] = (bytes[index].toInt() xor 1).toByte()
        writeBytes(bytes)
    }

    @Test
    fun `while off, nothing is encrypted`() = runTest {
        val encryption = device()

        assertEquals(Try.Success(null), encryption.encrypt(plain))
        assertNull(encryption.activeKeyId.first())
    }

    @Test
    fun `once on, backups are encrypted and open again on the same phone`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())

        val sealed = checkNotNull((encryption.encrypt(plain) as Try.Success).value)
        val opened = (encryption.decrypt(sealed.file, passphrase = null) as Try.Success).value

        assertEquals(encryption.activeKeyId.first(), sealed.keyId)
        assertEquals("the library, zipped", opened.readText())
    }

    @Test
    fun `only the wrapped key is stored, never the passphrase`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())

        val stored = File(folder.root, "phone.preferences_pb").readBytes().decodeToString()

        assertEquals(1, wrapper.wraps)
        assertFalse(stored.contains("correct horse"))
    }

    @Test
    fun `a new phone needs the passphrase, and refuses a wrong one`() = runTest {
        val old = device()
        old.enable("correct horse".toCharArray())
        val sealed = old.sealed()
        val keyId = checkNotNull(old.activeKeyId.first())
        val fresh = newPhone()

        assertFalse(fresh.canOpen(keyId))
        assertEquals(Try.Failure(AppError.PassphraseRequired), fresh.decrypt(sealed, null))
        assertEquals(
            Try.Failure(AppError.WrongPassphrase),
            fresh.decrypt(sealed, "correct house".toCharArray()),
        )
        val opened = fresh.decrypt(sealed, "correct horse".toCharArray())
        assertEquals("the library, zipped", (opened as Try.Success).value.readText())
        assertTrue("the key is kept for the restore that follows", fresh.canOpen(keyId))
    }

    @Test
    fun `restoring on a new phone keeps encryption on with the same key`() = runTest {
        val old = device()
        old.enable("correct horse".toCharArray())
        val sealed = old.sealed()
        val keyId = checkNotNull(old.activeKeyId.first())
        val fresh = newPhone()
        fresh.decrypt(sealed, "correct horse".toCharArray())

        fresh.adopt(keyId)

        assertEquals(keyId, fresh.activeKeyId.first())
        assertEquals(keyId, (fresh.encrypt(plain) as Try.Success).value?.keyId)
    }

    @Test
    fun `adopting does not replace a passphrase the user already set`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())
        val mine = encryption.activeKeyId.first()

        encryption.adopt("someone-elses-key")

        assertEquals(mine, encryption.activeKeyId.first())
    }

    @Test
    fun `a lost keystore key stops backups instead of sending them in the clear`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())

        wrapper.delete()

        assertEquals(Try.Failure(AppError.PassphraseRequired), encryption.encrypt(plain))
    }

    @Test
    fun `turning it off forgets the key, and old backups still open with the passphrase`() =
        runTest {
            val encryption = device()
            encryption.enable("correct horse".toCharArray())
            val sealed = encryption.sealed()

            encryption.disable()

            assertNull(encryption.activeKeyId.first())
            assertEquals(Try.Success(null), encryption.encrypt(plain))
            assertEquals(Try.Failure(AppError.PassphraseRequired), encryption.decrypt(sealed, null))
            assertTrue(encryption.decrypt(sealed, "correct horse".toCharArray()) is Try.Success)
        }

    @Test
    fun `changing the passphrase leaves older backups on the old one`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())
        val before = encryption.sealed()
        encryption.enable("battery staple".toCharArray())
        val fresh = newPhone()

        assertEquals(
            Try.Failure(AppError.WrongPassphrase),
            fresh.decrypt(before, "battery staple".toCharArray()),
        )
        assertTrue(fresh.decrypt(before, "correct horse".toCharArray()) is Try.Success)
    }

    @Test
    fun `a plain zip or a changed file is reported as a damaged backup`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())
        val damaged = encryption.sealed().apply { flipByteFromEnd(5) }

        assertEquals(Try.Failure(AppError.CorruptBackup), encryption.decrypt(plain, null))
        assertEquals(Try.Failure(AppError.CorruptBackup), encryption.decrypt(damaged, null))
    }

    @Test
    fun `a header asking for absurd settings is refused before any work`() = runTest {
        val greedy = KdfParams(memoryKib = 4 * 1024 * 1024, iterations = 3, parallelism = 1)
        val salt = ByteArray(BackupKey.SALT_SIZE)
        val header = EncryptedHeader(greedy, salt, BackupKey.keyIdOf(ByteArray(32)))
        val hostile = folder.newFile().apply { writeBytes(header.encode()) }

        val result = device().decrypt(hostile, "anything".toCharArray())

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
    }

    @Test
    fun `no decrypted file is left behind when opening fails`() = runTest {
        val encryption = device()
        encryption.enable("correct horse".toCharArray())
        val sealed = encryption.sealed().apply { flipByteFromEnd(5) }

        encryption.decrypt(sealed, null)

        val leftovers = File(folder.root, "phone-work").listFiles().orEmpty()
            .filter { it.name.startsWith("otium-restore") }
        assertTrue(leftovers.isEmpty())
    }
}
