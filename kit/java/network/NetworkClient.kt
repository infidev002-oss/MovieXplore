package your.app.network

import android.content.Context
import your.app.hooks.NetworkHooks
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

object NetworkClient {
    const val PLACEHOLDER_HOST = "tmdb.local"
    private const val PLACEHOLDER_BASE = "https://$PLACEHOLDER_HOST/"
    private lateinit var appContext: Context

    fun init(context: Context) { appContext = context.applicationContext }

    val okHttp: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cache(Cache(File(appContext.cacheDir, "tmdb_cache"), 20L * 1024 * 1024))
            .addInterceptor(TmdbInterceptor())
            .addInterceptor(OfflineCacheInterceptor())
            .addInterceptor(RateLimitInterceptor())
        NetworkHooks.interceptors.forEach { builder.addInterceptor(it) }   // keep this line, keep this order
        builder.addNetworkInterceptor(CacheTtlInterceptor())
        builder.build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val tmdbApi: TmdbApi by lazy { retrofit.create(TmdbApi::class.java) }
}
