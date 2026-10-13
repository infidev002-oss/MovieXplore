package com.movies.moviexplore.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.movies.moviexplore.config.RemoteValues
import kotlinx.coroutines.delay

/**
 * Splash interstitial. Call loadAndWait() after Remote Config and consent are done: it waits at most
 * RemoteValues.splashCounter seconds for the ad. Then showThen { navigate() } shows it (after the
 * 1 s overlay) if it loaded in time, or navigates straight away.
 * Splash delay: the "Ad showing…" notice never starts sooner than MIN_WAIT_MS after the load
 * started, even when the ad loads at once, so the splash (and its banner, loading at the same time) stays on screen
 * first. A later ad shows as soon as it loads; no ad means no wait.
 */
class SplashInterstitial {
    private var ad: InterstitialAd? = null
    private var failed = false

    suspend fun loadAndWait(activity: Activity) {
        if (!AdsConsent.canRequestAds(activity) || !RemoteValues.isAdUnitEnabled(RemoteValues.SPLASH_INTER)) return
        if (AdState.clickLimitReached(activity)) { Log.d(TAG, "splash ad: daily click cap reached, none"); return }
        val ids = RemoteValues.adUnitIds(RemoteValues.SPLASH_INTER)
        if (ids.isEmpty()) return
        val startedAt = System.currentTimeMillis()
        load(activity, ids, 0)
        val deadline = startedAt + RemoteValues.splashCounter * 1000
        while (ad == null && !failed && System.currentTimeMillis() < deadline) delay(250)
        val loadMs = System.currentTimeMillis() - startedAt
        if (ad == null) {                                        // no ad: navigate straight away, no extra wait
            Log.d(TAG, "splash ad: none after $loadMs ms, continuing")
            return
        }
        // Loaded early: hold the splash until MIN_WAIT_MS after the load started; then the notice, then the ad.
        val wait = startedAt + MIN_WAIT_MS - System.currentTimeMillis()
        Log.d(TAG, "splash ad: loaded in $loadMs ms, holding the splash ${wait.coerceAtLeast(0)} ms")
        if (wait > 0) delay(wait)
    }

    companion object {
        private const val TAG = "Inter"
        private const val MIN_WAIT_MS = 5000L
    }

    private fun load(activity: Activity, ids: List<String>, index: Int) {
        if (index >= ids.size) { failed = true; return }
        InterstitialAd.load(activity, ids[index], AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded }
            override fun onAdFailedToLoad(error: LoadAdError) { load(activity, ids, index + 1) }
        })
    }

    /** If the app went to the background during the notice, the ad shows 1 s after the user comes back. */
    fun showThen(activity: Activity, next: () -> Unit) {
        val current = ad ?: return next()
        ad = null
        InterstitialShow.run(activity, current, InterstitialShow.WhenInBackground.SHOW_ON_RETURN) { end, _ ->
            if (end != InterstitialShow.End.SCREEN_GONE) next()
        }
    }
}
