package com.movies.moviexplore.hooks

import okhttp3.Interceptor

object NetworkHooks {
    // Keep empty. Added to the shared OkHttpClient by NetworkClient.
    val interceptors: MutableList<Interceptor> = mutableListOf()
}
