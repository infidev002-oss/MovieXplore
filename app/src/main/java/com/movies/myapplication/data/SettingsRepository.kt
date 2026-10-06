package com.movies.myapplication.data

import android.content.SharedPreferences
import com.movies.myapplication.data.model.DiscoverFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user choices that should survive restarts. */
class SettingsRepository(private val prefs: SharedPreferences) {

    /** The country last picked in the watch-availability panel (ISO 3166-1 code). */
    private val _watchCountry = MutableStateFlow(prefs.getString(WATCH_COUNTRY, null) ?: DiscoverFilter.DEFAULT_WATCH_REGION)
    val watchCountry: StateFlow<String> = _watchCountry.asStateFlow()

    fun setWatchCountry(code: String) {
        _watchCountry.value = code
        prefs.edit().putString(WATCH_COUNTRY, code).apply()
    }

    private companion object {
        const val WATCH_COUNTRY = "watch_country"
    }
}
