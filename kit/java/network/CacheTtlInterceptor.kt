package your.app.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class CacheTtlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = chain.request().url
        val tmdbHost = TmdbConfig.baseUrl.toHttpUrlOrNull()?.host
        if (!response.isSuccessful || url.host != tmdbHost || url.encodedPath.contains("/search/")) return response
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$CACHE_TTL_SECONDS")
            .removeHeader("Pragma")
            .build()
    }
    companion object { const val CACHE_TTL_SECONDS = 6 * 60 * 60 }
}
