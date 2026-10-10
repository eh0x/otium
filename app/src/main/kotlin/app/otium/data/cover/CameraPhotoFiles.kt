package app.otium.data.cover

import android.content.ContentResolver
import android.content.Context
import androidx.core.net.toUri
import app.otium.domain.port.CapturedPhotos
import app.otium.domain.port.Clock
import app.otium.domain.port.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The folder `res/xml/file_paths.xml` shares with the camera app, under the same name. */
private const val CAMERA_DIRECTORY = "camera"
private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

/** The names `CoverSection` gives the photos it asks the camera app for. */
private val PHOTO_NAME = Regex("cover-\\d+\\.jpg")

/**
 * The photos the camera app writes into the app's cache for a cover. A photo is handed over as a
 * FileProvider address, so only an address with this app's authority, the camera folder and a
 * photo's name maps to a file; anything else is not ours to delete.
 */
@Singleton
class CameraPhotoFiles internal constructor(
    private val directory: File,
    private val authority: String,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : CapturedPhotos {

    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ) : this(
        File(context.cacheDir, CAMERA_DIRECTORY),
        "${context.packageName}.covers",
        clock,
        CoroutineScope(SupervisorJob() + dispatchers.io),
    )

    override fun discard(uris: Collection<String>) {
        val photos = uris.mapNotNull(::photoAt)
        if (photos.isNotEmpty()) scope.launch { photos.forEach(File::delete) }
    }

    /**
     * Deletes photos left behind by a process that died while the confirm screen was open. Only
     * old ones: a recent photo may belong to a screen being restored right now, which the camera
     * app has only just written.
     */
    fun purgeStale() {
        scope.launch {
            val cutoff = clock.nowMillis() - STALE_AFTER_MILLIS
            directory.listFiles().orEmpty()
                .filter { it.isFile && it.lastModified() < cutoff }
                .forEach(File::delete)
        }
    }

    /** The photo file [uri] stands for, or null when it is not a photo the camera took for us. */
    private fun photoAt(uri: String): File? {
        val parsed = uri.toUri()
        val segments = parsed.pathSegments
        val isPhoto = parsed.scheme == ContentResolver.SCHEME_CONTENT &&
            parsed.authority == authority &&
            segments.size == 2 &&
            segments[0] == CAMERA_DIRECTORY &&
            PHOTO_NAME.matches(segments[1])
        return if (isPhoto) File(directory, segments[1]) else null
    }
}
