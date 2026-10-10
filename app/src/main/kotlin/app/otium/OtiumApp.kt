package app.otium

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.otium.data.cover.CameraPhotoFiles
import app.otium.domain.port.LookupScheduler
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class OtiumApp :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {

    @Inject
    lateinit var httpClient: Lazy<OkHttpClient>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var lookupScheduler: LookupScheduler

    @Inject
    lateinit var cameraPhotos: CameraPhotoFiles

    override fun onCreate() {
        super.onCreate()
        // Books saved offline wait for a lookup; a run left over from an earlier session (or
        // given up after its last attempt) is planned again. Without such books it does nothing.
        lookupScheduler.schedule()
        // Cover photos a process that died never got to delete.
        cameraPhotos.purgeStale()
    }

    /** Workers get their dependencies from Hilt; the default initializer is off in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * Images load through the app's shared OkHttp client, which carries no API key. Remote covers
     * are only previewed (search results, the confirm screen) and kept in memory: the one cover
     * a book keeps is stored by the cover store when it is saved, so nothing else is written to
     * disk, as the Google Books terms ask.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient.get() })) }
            .diskCache(null)
            .build()
}
