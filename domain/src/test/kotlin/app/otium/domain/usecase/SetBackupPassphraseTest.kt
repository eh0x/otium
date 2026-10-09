package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupEncryption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetBackupPassphraseTest {

    private val encryption = FakeBackupEncryption()
    private val setPassphrase = SetBackupPassphrase(encryption)

    private suspend fun set(passphrase: String, confirmation: String = passphrase) =
        setPassphrase(passphrase.toCharArray(), confirmation.toCharArray())

    @Test
    fun `a long enough passphrase typed twice turns encryption on`() = runTest {
        assertEquals(Try.Success(Unit), set("correct horse"))

        assertEquals(FakeBackupEncryption.keyIdOf("correct horse"), encryption.activeKeyId.value)
    }

    @Test
    fun `exactly the minimum length is accepted`() = runTest {
        assertEquals(Try.Success(Unit), set("a".repeat(SetBackupPassphrase.MIN_LENGTH)))
    }

    @Test
    fun `a short passphrase is refused`() = runTest {
        val result = set("a".repeat(SetBackupPassphrase.MIN_LENGTH - 1))

        assertEquals(
            Try.Failure(AppError.InvalidInput(SetBackupPassphrase.FIELD_PASSPHRASE)),
            result,
        )
        assertNull(encryption.activeKeyId.value)
    }

    @Test
    fun `a passphrase of only spaces is refused`() = runTest {
        val result = set(" ".repeat(SetBackupPassphrase.MIN_LENGTH + 4))

        assertEquals(
            Try.Failure(AppError.InvalidInput(SetBackupPassphrase.FIELD_PASSPHRASE)),
            result,
        )
    }

    @Test
    fun `a confirmation that differs is refused`() = runTest {
        val result = set("correct horse", "correct house")

        assertEquals(
            Try.Failure(AppError.InvalidInput(SetBackupPassphrase.FIELD_CONFIRMATION)),
            result,
        )
        assertNull(encryption.activeKeyId.value)
    }

    @Test
    fun `setting it again replaces the key`() = runTest {
        set("correct horse")

        set("battery staple")

        assertEquals(FakeBackupEncryption.keyIdOf("battery staple"), encryption.activeKeyId.value)
    }
}
