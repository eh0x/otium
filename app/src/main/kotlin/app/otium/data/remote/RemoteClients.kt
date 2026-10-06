package app.otium.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val TIMEOUT_SECONDS = 8L
private const val JSON_MEDIA_TYPE = "application/json"

/** How the app builds its HTTP clients, in one place so the app and its tests agree. */
internal object RemoteClients {

    /**
     * `Otium/0.1.0 (contact)`. Open Library asks clients to say who they are and how to reach them,
     * and gives identified clients a higher request limit.
     */
    fun userAgent(appVersion: String, contact: String): String =
        if (contact.isBlank()) "Otium/$appVersion" else "Otium/$appVersion (${contact.trim()})"

    /** Every call, redirects included, gives up after [TIMEOUT_SECONDS] seconds. */
    fun httpClient(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor(userAgent))
        .build()

    /**
     * [base] with the Google Books key added to every request. With no key the client is [base]
     * unchanged: the key is optional, and a request without one is simply anonymous.
     */
    fun googleClient(base: OkHttpClient, apiKey: String): OkHttpClient = if (apiKey.isBlank()) {
        base
    } else {
        base.newBuilder().addInterceptor(ApiKeyInterceptor(apiKey)).build()
    }

    fun retrofit(baseUrl: HttpUrl, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(RemoteJson.json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()
}

private class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder().header("User-Agent", userAgent).build(),
    )
}

private class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder().addQueryParameter("key", apiKey).build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
