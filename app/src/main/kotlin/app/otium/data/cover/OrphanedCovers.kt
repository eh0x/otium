package app.otium.data.cover

import android.content.Context
import android.database.sqlite.SQLiteException
import app.otium.data.local.dao.BookDao
import app.otium.domain.port.Clock
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.usecase.BackupLock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A cover file younger than this may belong to a save or a restore that has not committed. */
private const val SETTLED_AFTER_MILLIS = 24L * 60 * 60 * 1000

/**
 * Deletes cover files that no book names. The book repository deletes the covers its own writes
 * leave unused; what is left for this are covers of a save whose process died before the book was
 * written, and covers of books deleted before the repository took care of them.
 *
 * Only old files go, and only while no backup or restore runs: a restore copies covers in before
 * the rows that name them are committed.
 */
@Singleton
class OrphanedCovers internal constructor(
    private val dao: BookDao,
    private val covers: CoverFiles,
    private val lock: BackupLock,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {

    @Inject
    internal constructor(
        dao: BookDao,
        @ApplicationContext context: Context,
        lock: BackupLock,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ) : this(
        dao,
        CoverFiles(coversDirectory(context)),
        lock,
        clock,
        CoroutineScope(SupervisorJob() + dispatchers.io),
    )

    /** Starts [purgeNow] in the background. */
    fun purge() {
        scope.launch { purgeNow() }
    }

    /**
     * Deletes the settled cover files no book names and returns how many went. Nothing is deleted
     * when the library cannot be read.
     */
    @Suppress("SwallowedException") // Without the library nothing is known to be unused.
    internal suspend fun purgeNow(): Int = lock.withLock {
        val cutoff = clock.nowMillis() - SETTLED_AFTER_MILLIS
        val settled = covers.list().filter { it.lastModified() < cutoff }
        if (settled.isEmpty()) return@withLock 0
        val named = try {
            dao.coverPaths().mapNotNullTo(HashSet(), covers::nameOf)
        } catch (error: SQLiteException) {
            return@withLock 0
        }
        settled.filter { it.name !in named }.count { covers.delete(it.name) }
    }
}
