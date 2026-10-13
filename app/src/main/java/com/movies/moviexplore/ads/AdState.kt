package com.movies.moviexplore.ads

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.movies.moviexplore.hooks.AdRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared ad state:
 * - a full-screen ad is on screen (never open the welcome screen over it),
 * - the user tapped an ad (returning from the browser / Play Store must not open the welcome screen),
 * - ad taps today, for the daily click cap (AdRules.maxAdClicksPerDay, Remote Config "MaxAdClicksPerDay"):
 *   once reached, no more interstitials until tomorrow — protects against invalid click traffic.
 * The kit's ad classes update it; app code doesn't need to.
 */
object AdState {
    private const val PREFS = "ads"
    private const val KEY_CLICK_DAY = "click_day"
    private const val KEY_CLICK_COUNT = "click_count"

    @Volatile var fullScreenAdShowing = false
        internal set

    @Volatile private var adClicked = false

    /** Call on every ad tap (banner, native, interstitial). [context] is needed to count it for the daily cap. */
    fun markAdClicked(context: Context? = null) {
        adClicked = true
        context ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = today()
        val count = (if (prefs.getString(KEY_CLICK_DAY, null) == today) prefs.getInt(KEY_CLICK_COUNT, 0) else 0) + 1
        prefs.edit { putString(KEY_CLICK_DAY, today); putInt(KEY_CLICK_COUNT, count) }
        Log.d("Inter", "ad click #$count today (cap ${AdRules.maxAdClicksPerDay().takeIf { it > 0 } ?: "off"})")
    }

    /** Returns true once after an ad tap, then resets. */
    fun consumeAdClick(): Boolean {
        val clicked = adClicked
        adClicked = false
        return clicked
    }

    /** True when the daily click cap is on and today's ad taps reached it: show no interstitials until tomorrow. */
    fun clickLimitReached(context: Context): Boolean {
        val max = AdRules.maxAdClicksPerDay()
        if (max <= 0) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_CLICK_DAY, null) != today()) return false
        return prefs.getInt(KEY_CLICK_COUNT, 0) >= max
    }

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
