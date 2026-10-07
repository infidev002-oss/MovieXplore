package com.movies.moviexplore.data

import com.movies.moviexplore.network.TmdbConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the onboarding choices to every TMDB request ([ContentLocale]): `language` everywhere, so titles,
 * overviews and genre names come translated where TMDB has a translation, and `region` on movie lists, so
 * popular, top rated, discover and search follow the picked country's releases. Parameters a request
 * already sets are left alone. Registered through NetworkHooks.interceptors, so it runs after the kit's
 * TMDB interceptor has filled in the real host and key.
 */
class ContentLocaleInterceptor : Interceptor {

    private val tmdbHost by lazy { TmdbConfig.baseUrl.toHttpUrl().host }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != tmdbHost) return chain.proceed(request)

        val builder = url.newBuilder()
        ContentLocale.language?.let { if (url.queryParameter("language") == null) builder.addQueryParameter("language", it) }
        if (url.queryParameter("region") == null && MOVIE_LISTS.any { url.encodedPath.endsWith(it) }) {
            builder.addQueryParameter("region", ContentLocale.country)
        }
        return chain.proceed(request.newBuilder().url(builder.build()).build())
    }

    private companion object {
        // TMDB only applies `region` to these movie endpoints; TV lists and trending have no region.
        val MOVIE_LISTS = listOf("/movie/popular", "/movie/top_rated", "/movie/now_playing", "/movie/upcoming", "/discover/movie", "/search/movie")
    }
}
