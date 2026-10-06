package app.otium.di

import app.otium.BuildConfig
import app.otium.data.remote.RemoteClients
import app.otium.data.remote.googlebooks.GOOGLE_BOOKS_BASE_URL
import app.otium.data.remote.googlebooks.GoogleBooksApi
import app.otium.data.remote.openlibrary.OPEN_LIBRARY_BASE_URL
import app.otium.data.remote.openlibrary.OpenLibraryApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * The network layer. One OkHttp client is shared, so connections are reused. Google Books gets
 * the same client with the optional API key added; Open Library needs no key.
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    internal fun provideHttpClient(): OkHttpClient = RemoteClients.httpClient(
        RemoteClients.userAgent(BuildConfig.VERSION_NAME, BuildConfig.OPEN_LIBRARY_CONTACT),
    )

    @Provides
    @Singleton
    internal fun provideGoogleBooksApi(client: OkHttpClient): GoogleBooksApi =
        RemoteClients.retrofit(
            GOOGLE_BOOKS_BASE_URL.toHttpUrl(),
            RemoteClients.googleClient(client, BuildConfig.BOOKS_API_KEY),
        ).create(GoogleBooksApi::class.java)

    @Provides
    @Singleton
    internal fun provideOpenLibraryApi(client: OkHttpClient): OpenLibraryApi =
        RemoteClients.retrofit(OPEN_LIBRARY_BASE_URL.toHttpUrl(), client)
            .create(OpenLibraryApi::class.java)
}
