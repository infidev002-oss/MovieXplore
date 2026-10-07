package com.movies.moviexplore.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user choices that should survive restarts. */
class SettingsRepository private constructor(private val prefs: SharedPreferences) {

    init {
        // Loaded before any TMDB request, so the first lists already come in the picked language and country.
        ContentLocale.language = prefs.getString(CONTENT_LANGUAGE, null)
        prefs.getString(COUNTRY, null)?.let { ContentLocale.country = it }
    }

    /** The country last picked in the watch-availability panel (ISO 3166-1 code); starts on the app's country. */
    private val _watchCountry = MutableStateFlow(prefs.getString(WATCH_COUNTRY, null) ?: ContentLocale.country)
    val watchCountry: StateFlow<String> = _watchCountry.asStateFlow()

    fun setWatchCountry(code: String) {
        _watchCountry.value = code
        prefs.edit().putString(WATCH_COUNTRY, code).apply()
    }

    /** True once a content language has been picked in onboarding. */
    val languageChosen: Boolean get() = prefs.contains(CONTENT_LANGUAGE)

    fun setContentLanguage(code: String) {
        ContentLocale.language = code
        prefs.edit().putString(CONTENT_LANGUAGE, code).apply()
    }

    /** True once a country has been picked in onboarding. */
    val countryChosen: Boolean get() = prefs.contains(COUNTRY)

    /** The app's country; where-to-watch follows it until the user picks another country there. */
    fun setCountry(code: String) {
        ContentLocale.country = code
        prefs.edit().putString(COUNTRY, code).apply()
        setWatchCountry(code)
    }

    /** True once the user has finished or skipped onboarding; later launches go straight to Home. */
    val onboardingDone: Boolean get() = prefs.getBoolean(ONBOARDING_DONE, false)

    fun setOnboardingDone() {
        prefs.edit().putBoolean(ONBOARDING_DONE, true).apply()
    }

    companion object {
        private const val WATCH_COUNTRY = "watch_country"
        private const val CONTENT_LANGUAGE = "content_language"
        private const val COUNTRY = "country"
        private const val ONBOARDING_DONE = "onboarding_done"

        @Volatile private var instance: SettingsRepository? = null

        /** The app's one instance, created on first use. */
        fun get(context: Context): SettingsRepository = instance ?: synchronized(this) {
            instance ?: SettingsRepository(
                context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
