package com.movies.moviexplore.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class TmdbInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.url.host != NetworkClient.PLACEHOLDER_HOST) return chain.proceed(req)
        val base = TmdbConfig.baseUrl.toHttpUrl()
        val url = req.url.newBuilder()
            .scheme(base.scheme).host(base.host).port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + req.url.encodedPath)
            .setQueryParameter("api_key", TmdbConfig.apiKey)
            .build()
        return chain.proceed(req.newBuilder().url(url).build())
    }
}
