package com.movies.moviexplore.network

import com.movies.moviexplore.BuildConfig

object TmdbConfig {
    @Volatile var baseUrl: String = BuildConfig.TMDB_BASE_URL
    @Volatile var apiKey: String = BuildConfig.TMDB_API_KEY
}
