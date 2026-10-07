package com.movies.moviexplore.data

import com.movies.moviexplore.data.model.DiscoverFilter
import java.util.Locale

/**
 * The content language and country picked during onboarding, readable from anywhere (the TMDB interceptor,
 * region defaults). [SettingsRepository] loads and saves them; nothing else should set them.
 */
object ContentLocale {
    /** ISO 639-1 code TMDB returns titles and overviews in; null until picked (TMDB's default, English). */
    @Volatile var language: String? = null
        internal set

    /** ISO 3166-1 code used for movie lists, release info and where-to-watch. */
    @Volatile var country: String = DiscoverFilter.DEFAULT_WATCH_REGION
        internal set

    /** Languages offered in onboarding (all supported by TMDB), in this order. */
    val languageCodes = listOf(
        "en", "hi", "bn", "te", "mr", "ta", "gu", "kn", "ml", "pa", "ur",
        "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "tr", "id", "th", "vi", "nl", "pl",
    )

    /** The device language when it is offered, else English. */
    fun deviceLanguage(): String = Locale.getDefault().language.takeIf { it in languageCodes } ?: "en"

    /** The device country when it is a real ISO country, else the default region. */
    fun deviceCountry(): String =
        Locale.getDefault().country.takeIf { it in Locale.getISOCountries() } ?: DiscoverFilter.DEFAULT_WATCH_REGION
}
