package com.movies.moviexplore.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class OfflineCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            val cachedReq = request.newBuilder()
                .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(7, TimeUnit.DAYS).build())
                .build()
            val cached = chain.proceed(cachedReq)
            if (cached.code == 504) { cached.close(); throw e }
            cached
        }
    }
}
