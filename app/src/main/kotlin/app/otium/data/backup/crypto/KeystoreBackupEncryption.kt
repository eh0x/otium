package app.otium.data.backup.crypto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otium.data.backup.BackupFiles
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.port.BackupEncryption
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.EncryptedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The preferences file that holds the wrapped backup key, kept apart from the library settings so
 * that nothing which moves those settings (such as a future device transfer) can carry it along.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupKeyStore

/**
 * Backup encryption with the key kept on this device. The key derived from the passphrase is
 * stored wrapped by [KeyWrapper] (the Android Keystore in the app), next to its salt and Argon2id
 * settings, never in the clear and never with the passphrase. These entries belong to this device
 * and are not part of any backup.
 *
 * A backup opened with a passphrase keeps its key in memory for the rest of the process, so the
 * restore that follows, and [adopt], do not ask again.
 */
@Singleton
class KeystoreBackupEncryption internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val wrapper: KeyWrapper,
    private val work: File,
    private val dispatchers: DispatcherProvider,
    private val params: KdfParams = KdfParams.DEFAULT,
    private val cipher: BackupCipher = BackupCipher(),
) : BackupEncryption {

    @Inject
    internal constructor(
        @BackupKeyStore dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ) : this(dataStore, AndroidKeystoreKeyWrapper(), BackupFiles.of(context).work, dispatchers)

    private val unlocked = ConcurrentHashMap<String, BackupKey>()

    override val activeKeyId: Flow<String?> = dataStore.data.map { it[KEY_ID] }

    override suspend fun enable(passphrase: CharArray): Try<Unit> {
        val key = withContext(dispatchers.default) { BackupKey.create(passphrase, params) }
        return store(key)
    }

    override suspend fun disable() {
        dataStore.edit { preferences -> STORED_KEYS.forEach { preferences.remove(it) } }
        withContext(dispatchers.io) { wrapper.delete() }
    }

    override suspend fun encrypt(plain: File): Try<EncryptedFile?> = activeKey().flatMap { key ->
        if (key == null) Try.Success(null) else seal(plain, key)
    }

    override suspend fun canOpen(keyId: String): Boolean = heldKey(keyId) != null

    override suspend fun decrypt(encrypted: File, passphrase: CharArray?): Try<File> {
        val header = withContext(dispatchers.io) { cipher.readHeader(encrypted) }
            ?.takeIf { it.params.isAcceptable }
            ?: return Try.Failure(AppError.CorruptBackup)
        return keyFor(header, passphrase).flatMap { key -> open(encrypted, key) }
    }

    override suspend fun adopt(keyId: String) {
        if (dataStore.data.first()[KEY_ID] != null) return
        unlocked[keyId]?.let { store(it) }
    }

    /** A key for [keyId] this device can use without a passphrase, or null. */
    private suspend fun heldKey(keyId: String): BackupKey? = unlocked[keyId]
        ?: (activeKey() as? Try.Success)?.value?.takeIf { it.id == keyId }

    /** The key for [header]: one at hand, or the one [passphrase] gives if it is the right one. */
    private suspend fun keyFor(header: EncryptedHeader, passphrase: CharArray?): Try<BackupKey> {
        val held = heldKey(header.keyId)
        return when {
            held != null -> Try.Success(held)
            passphrase == null -> Try.Failure(AppError.PassphraseRequired)
            else -> derive(header, passphrase)
        }
    }

    private suspend fun derive(header: EncryptedHeader, passphrase: CharArray): Try<BackupKey> {
        val derived = withContext(dispatchers.default) {
            BackupKey.derive(passphrase, header.salt, header.params)
        }
        return if (derived.id == header.keyId) {
            unlocked[derived.id] = derived
            Try.Success(derived)
        } else {
            Try.Failure(AppError.WrongPassphrase)
        }
    }

    /**
     * The key new backups use, or null while encryption is off. Fails with `PassphraseRequired`
     * when it is stored but can no longer be unwrapped, for example after the keystore was reset.
     */
    @Suppress("SwallowedException") // The user is asked for the passphrase; the cause is moot.
    private suspend fun activeKey(): Try<BackupKey?> {
        val stored = dataStore.data.first()
        val id = stored[KEY_ID] ?: return Try.Success(null)
        unlocked[id]?.let { return Try.Success(it) }
        val key = try {
            withContext(dispatchers.io) { stored.toKey() }
        } catch (error: GeneralSecurityException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
        return if (key != null && key.id == id) {
            Try.Success(key)
        } else {
            Try.Failure(AppError.PassphraseRequired)
        }
    }

    private suspend fun store(key: BackupKey): Try<Unit> {
        val wrapped = try {
            withContext(dispatchers.io) { wrapper.wrap(key.bytes) }
        } catch (error: GeneralSecurityException) {
            return Try.Failure(AppError.Unexpected(error.message ?: "Could not protect the key"))
        }
        dataStore.edit {
            it[KEY_ID] = key.id
            it[WRAPPED_KEY] = Base64.getEncoder().encodeToString(wrapped)
            it[SALT] = key.salt.toHex()
            it[MEMORY_KIB] = key.params.memoryKib
            it[ITERATIONS] = key.params.iterations
            it[PARALLELISM] = key.params.parallelism
        }
        return Try.Success(Unit)
    }

    private fun Preferences.toKey(): BackupKey? {
        val wrapped = this[WRAPPED_KEY]?.let(Base64.getDecoder()::decode) ?: return null
        val salt = this[SALT]?.hexToBytes() ?: return null
        val params = KdfParams(
            memoryKib = this[MEMORY_KIB] ?: return null,
            iterations = this[ITERATIONS] ?: return null,
            parallelism = this[PARALLELISM] ?: return null,
        )
        return BackupKey(wrapper.unwrap(wrapped), salt, params)
    }

    @Suppress("SwallowedException") // Reported as a storage problem; details are not shown.
    private suspend fun seal(plain: File, key: BackupKey): Try<EncryptedFile> =
        withContext(dispatchers.io) {
            val target = File.createTempFile("otium-encrypted", ".bin", work.apply { mkdirs() })
            try {
                cipher.encrypt(plain, target, key)
                Try.Success(EncryptedFile(target, key.id))
            } catch (error: IOException) {
                target.delete()
                Try.Failure(AppError.Storage(error.message ?: "Could not encrypt the backup"))
            } catch (error: GeneralSecurityException) {
                target.delete()
                Try.Failure(AppError.Storage(error.message ?: "Could not encrypt the backup"))
            }
        }

    /** Decrypts into a new file. Any failure means the data was changed: a damaged backup. */
    @Suppress("SwallowedException") // The user is told the backup is damaged, not why.
    private suspend fun open(encrypted: File, key: BackupKey): Try<File> =
        withContext(dispatchers.io) {
            val target = File.createTempFile("otium-restore", ".zip", work.apply { mkdirs() })
            try {
                cipher.decrypt(encrypted, target, key)
                Try.Success(target)
            } catch (error: IOException) {
                target.delete()
                Try.Failure(AppError.CorruptBackup)
            } catch (error: GeneralSecurityException) {
                target.delete()
                Try.Failure(AppError.CorruptBackup)
            }
        }

    private companion object {
        val KEY_ID = stringPreferencesKey("backup_key_id")
        val WRAPPED_KEY = stringPreferencesKey("backup_key_wrapped")
        val SALT = stringPreferencesKey("backup_key_salt")
        val MEMORY_KIB = intPreferencesKey("backup_key_memory_kib")
        val ITERATIONS = intPreferencesKey("backup_key_iterations")
        val PARALLELISM = intPreferencesKey("backup_key_parallelism")
        val STORED_KEYS = listOf(KEY_ID, WRAPPED_KEY, SALT, MEMORY_KIB, ITERATIONS, PARALLELISM)
    }
}
