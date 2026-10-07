package your.app.network

import your.app.BuildConfig

object TmdbConfig {
    @Volatile var baseUrl: String = BuildConfig.TMDB_BASE_URL
    @Volatile var apiKey: String = BuildConfig.TMDB_API_KEY
}
