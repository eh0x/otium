package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import app.otium.domain.port.BackupEncryption
import app.otium.domain.port.EncryptedFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pretend encryption: an "encrypted" file is the plain text behind `ENC:<key id>:`, and the key id
 * of a passphrase is `key-<passphrase>`. Enough to test who encrypts, with which key, and when a
 * passphrase is asked for.
 */
class FakeBackupEncryption : BackupEncryption {

    private val active = MutableStateFlow<String?>(null)
    private val unlocked = mutableSetOf<String>()

    override val activeKeyId: StateFlow<String?> = active

    /** Set to make [encrypt] fail as if the stored key had become unusable. */
    var keyLost = false

    /** Files [encrypt] and [decrypt] have produced, so tests can check they were cleaned up. */
    val produced = mutableListOf<File>()

    /** Turns encryption on as if [passphrase] had been set. */
    fun turnOn(passphrase: String) {
        active.value = keyIdOf(passphrase)
    }

    override suspend fun enable(passphrase: CharArray): Try<Unit> {
        active.value = keyIdOf(String(passphrase))
        return Try.Success(Unit)
    }

    override suspend fun disable() {
        active.value = null
    }

    override suspend fun encrypt(plain: File): Try<EncryptedFile?> {
        val keyId = active.value ?: return Try.Success(null)
        if (keyLost) return Try.Failure(AppError.PassphraseRequired)
        val file = tempFile().apply { writeText("$PREFIX$keyId:${plain.readText()}") }
        return Try.Success(EncryptedFile(file, keyId))
    }

    override suspend fun canOpen(keyId: String): Boolean = keyId in held()

    override suspend fun decrypt(encrypted: File, passphrase: CharArray?): Try<File> {
        val text = encrypted.takeIf { it.exists() }?.readText().orEmpty()
        if (!text.startsWith(PREFIX)) return Try.Failure(AppError.CorruptBackup)
        val keyId = text.removePrefix(PREFIX).substringBefore(':')
        val refusal = when {
            keyId in held() -> null
            passphrase == null -> AppError.PassphraseRequired
            keyIdOf(String(passphrase)) == keyId -> null.also { unlocked += keyId }
            else -> AppError.WrongPassphrase
        }
        return refusal?.let { Try.Failure(it) }
            ?: Try.Success(tempFile().apply { writeText(text.removePrefix("$PREFIX$keyId:")) })
    }

    override suspend fun adopt(keyId: String) {
        if (active.value == null && keyId in unlocked) active.value = keyId
    }

    private fun held(): Set<String> = unlocked + listOfNotNull(active.value)

    private fun tempFile(): File =
        File.createTempFile("fake-crypto", ".bin").apply { deleteOnExit() }.also(produced::add)

    companion object {
        private const val PREFIX = "ENC:"

        fun keyIdOf(passphrase: String) = "key-$passphrase"

        /** What [encrypt] would write for [info] under [passphrase], to plant in a fake Drive. */
        fun encryptedContent(info: BackupInfo, passphrase: String) =
            "$PREFIX${keyIdOf(passphrase)}:${FakeBackupFiles.encode(info)}"
    }
}
