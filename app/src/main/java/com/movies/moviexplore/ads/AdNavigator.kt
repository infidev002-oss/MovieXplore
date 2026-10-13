package com.movies.moviexplore.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.hooks.AdRules
import androidx.core.content.edit

/**
 * Interstitial on navigation.
 * - open(activity, intent): every ad-carrying screen change goes through this.
 * - showThen(activity) { … }: same rules for non-intent moments (e.g. switching tabs).
 * Rules: AdRules.beforeInterstitial first; then the interstitial if enabled, loaded, not expired and
 * at least AdRules.interAdInterval() ms since the last one; a 1 s "Ad showing…" notice precedes it (Back closes only
 * the notice). Never shown while the app is in the background, nor after the daily click cap (AdRules.maxAdClicksPerDay).
 * Loading: after consent (splash), after each show, when the app returns to the foreground, and on a tap when
 * there is no ad — never sooner than 30 s after a failed load. Logcat tag "Inter".
 */
object AdNavigator {
    private const val TAG = "Inter"
    private const val PREFS = "ads"
    private const val KEY_LAST_SHOWN = "inter_last_shown_ms"
    private const val EXPIRY_MS = 55 * 60 * 1000L     // AdMob interstitials expire after ~1 hour
    private const val RETRY_COOLDOWN_MS = 30 * 1000L  // Google advises against rapid retries after a failed load

    private var interstitial: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var lastFailAt = 0L
    private var showing = false

    /** Call after consent (splash). Safe to call often: no-op when an ad is fresh, loading, or in the cooldown. */
    fun preload(context: Context) {
        (context.applicationContext as? Application)?.let { ScreenTracker.install(it) }
        if (loading) return
        if (interstitial != null && System.currentTimeMillis() - loadedAt < EXPIRY_MS) return
        interstitial = null
        if (!AdsConsent.canRequestAds(context) || !RemoteValues.isAdUnitEnabled(RemoteValues.INTER)) return
        if (AdState.clickLimitReached(context)) { Log.d(TAG, "load skipped: daily click cap reached"); return }
        val ids = RemoteValues.adUnitIds(RemoteValues.INTER)
        if (ids.isEmpty()) return
        val sinceFail = System.currentTimeMillis() - lastFailAt
        if (sinceFail in 0 until RETRY_COOLDOWN_MS) {
            Log.d(TAG, "load skipped: cooldown after failure (${sinceFail / 1000} s of ${RETRY_COOLDOWN_MS / 1000} s)")
            return
        }
        loading = true
        Log.d(TAG, "load start, ids=${ids.size}")
        load(context.applicationContext, ids, 0)
    }

    private fun load(context: Context, ids: List<String>, index: Int) {
        if (index >= ids.size) {
            loading = false
            lastFailAt = System.currentTimeMillis()
            Log.w(TAG, "load failed: all ${ids.size} ids failed")
            return
        }
        InterstitialAd.load(context, ids[index], AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                loadedAt = System.currentTimeMillis()
                loading = false
                lastFailAt = 0L
                Log.d(TAG, "loaded: id #$index")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "id #$index failed: code=${error.code} ${error.message}")
                load(context, ids, index + 1)
            }
        })
    }

    /** Opens [intent] with the interstitial before it when due. After a rotation it opens from the new screen. */
    fun open(activity: Activity, intent: Intent) = showThenOn(activity) { screen ->
        try { screen.startActivity(intent) } catch (e: Exception) { Log.w(TAG, "open failed", e) }
    }

    /** Runs [next] after the interstitial when due (or straight away). */
    fun showThen(activity: Activity, next: () -> Unit) = showThenOn(activity) { next() }

    private fun showThenOn(activity: Activity, next: (Activity) -> Unit) {
        if (showing) return
        if (AdRules.beforeInterstitial(activity)) { next(activity); return }

        val ad = interstitial?.takeIf { System.currentTimeMillis() - loadedAt < EXPIRY_MS }
        val due = isInterstitialDue(activity)
        Log.d(TAG, "show check: adReady=${ad != null} due=$due loading=$loading from=${activity.javaClass.simpleName}")
        if (ad == null || !due || !RemoteValues.isAdUnitEnabled(RemoteValues.INTER) || AdState.clickLimitReached(activity)) {
            next(activity)
            preload(activity)                          // missing / stale / failed earlier: retry (cooldown applies)
            return
        }

        showing = true
        InterstitialShow.run(activity, ad) { end, screen ->
            showing = false
            when (end) {
                InterstitialShow.End.CLOSED -> {
                    if (interstitial === ad) interstitial = null
                    screen?.let(next)
                    preload(screen ?: activity)
                }
                InterstitialShow.End.BACKGROUND -> Unit          // app in the background: ad kept, stay on this screen
                InterstitialShow.End.SCREEN_GONE -> screen?.let(next)   // recreated screen: open without an ad
            }
        }
    }

    /**
     * True when at least AdRules.interAdInterval() ms passed since the last interstitial of any kind
     * (navigation, splash or welcome back). The interval hook also covers per-user intervals set by the app.
     */
    fun isInterstitialDue(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SHOWN, 0L)
        val elapsed = System.currentTimeMillis() - last
        return elapsed < 0 || elapsed >= AdRules.interAdInterval()   // < 0: clock moved back, don't block ads
    }

    /** Records an interstitial shown now, so the next one waits AdRules.interAdInterval() (used by WelcomeBack too). */
    fun markInterstitialShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_LAST_SHOWN, System.currentTimeMillis()) }
    }
}
