package app.otium

import android.app.Application
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
    SingletonImageLoader.Factory {

    @Inject
    lateinit var httpClient: Lazy<OkHttpClient>

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
