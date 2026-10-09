package app.otium.domain.port

import app.otium.domain.error.Try
import java.io.File
import kotlinx.coroutines.flow.Flow

/** A backup file encrypted with the key named [keyId]. */
data class EncryptedFile(val file: File, val keyId: String)

/**
 * Optional passphrase encryption for backups. Turning it on derives a key from the passphrase and
 * keeps it on this device, protected by the system keystore, so scheduled backups can encrypt
 * without asking. The passphrase itself is never stored, and a forgotten one cannot be recovered.
 * Backups already in Drive keep the key they were made with.
 */
interface BackupEncryption {

    /** The id of the key new backups are encrypted with, or null while encryption is off. */
    val activeKeyId: Flow<String?>

    /**
     * Turns encryption on with a key derived from [passphrase], replacing any earlier key. Slow
     * on purpose (about a second). Does not keep [passphrase]; the caller clears it.
     */
    suspend fun enable(passphrase: CharArray): Try<Unit>

    /** Turns encryption off and forgets the key. Backups already made stay encrypted. */
    suspend fun disable()

    /**
     * Encrypts [plain] into a new temporary file with the active key; the caller deletes both.
     * Succeeds with null while encryption is off. Fails with `PassphraseRequired` when encryption
     * is on but the stored key can no longer be used (the user must enter the passphrase again).
     */
    suspend fun encrypt(plain: File): Try<EncryptedFile?>

    /** True when a backup encrypted with [keyId] can be opened without asking for a passphrase. */
    suspend fun canOpen(keyId: String): Boolean

    /**
     * Decrypts [encrypted] into a new temporary file; the caller deletes both. Uses a key this
     * device holds when one fits, otherwise [passphrase]. Fails with `PassphraseRequired` when
     * neither fits, `WrongPassphrase` when [passphrase] does not open it, and `CorruptBackup`
     * when the file was changed or is not an encrypted backup.
     */
    suspend fun decrypt(encrypted: File, passphrase: CharArray?): Try<File>

    /**
     * After restoring a backup encrypted with [keyId], keeps encrypting new backups with that
     * key if encryption is off on this device, so moving to a new phone does not quietly turn it
     * off. Does nothing when encryption is already on or the key is not at hand.
     */
    suspend fun adopt(keyId: String)
}
