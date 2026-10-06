package com.movies.myapplication

import android.app.Application
import android.content.Context
import com.movies.myapplication.data.AccountRepository
import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.SettingsRepository
import com.movies.myapplication.data.WatchlistRepository
import com.movies.myapplication.data.remote.TmdbApi
import com.movies.myapplication.ui.common.ImageLoader
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.create
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MovieApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ImageLoader.init(container.imageHttpClient)
    }
}

/** The app's single [AppContainer], from any Context. */
val Context.appContainer: AppContainer get() = (applicationContext as MovieApp).container

/** Manual dependency graph: one shared HTTP setup, the API and the repositories for the whole app. */
class AppContainer(context: Context) {

    /**
     * Shared by API calls and image downloads. A connection left idle can be dropped silently by
     * Wi-Fi or a router; without these settings the next request hangs on it until it times out.
     */
    private val baseHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // HTTP/2 pings notice a dead connection within seconds, so it is replaced before a request waits on it.
        .pingInterval(10, TimeUnit.SECONDS)
        .addInterceptor(RetryOnceInterceptor)
        .build()

    /** Posters and photos: a large disk cache, and no API key (it only goes to TMDB's API). */
    val imageHttpClient: OkHttpClient = baseHttpClient.newBuilder()
        .cache(Cache(File(context.cacheDir, "images"), 50L * 1024 * 1024))
        .build()

    private val apiHttpClient = baseHttpClient.newBuilder()
        .cache(Cache(File(context.cacheDir, "tmdb_http"), 10L * 1024 * 1024))
        // The key is attached here, in one place, and only ever sent over HTTPS to TMDB.
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.newBuilder()
                .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                .build()
            chain.proceed(request.newBuilder().url(url).build())
        }
        .build()

    // No converter library: endpoints return raw bodies, parsed with org.json in MediaRepository.
    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(apiHttpClient)
        .build()
        .create()

    val mediaRepository = MediaRepository(api)

    val watchlist = WatchlistRepository(context.getSharedPreferences("watchlist", Context.MODE_PRIVATE))

    val account = AccountRepository(context.getSharedPreferences("account", Context.MODE_PRIVATE))

    val settings = SettingsRepository(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
}

/**
 * Retries a GET once when the connection stalls or drops mid-request; OkHttp gives the retry a fresh
 * connection. A request the user abandoned (cancelled) is not retried.
 */
private object RetryOnceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (request.method != "GET" || chain.call().isCanceled()) throw e
            chain.proceed(request)
        }
    }
}
