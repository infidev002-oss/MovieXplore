package com.movies.moviexplore.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.movies.moviexplore.config.RemoteValues
import kotlinx.coroutines.delay

/**
 * Splash interstitial. Call loadAndWait() after Remote Config and consent are done: it waits at most
 * RemoteValues.splashCounter seconds for the ad. Then showThen { navigate() } shows it (after the
 * 1 s overlay) if it loaded in time, or navigates straight away.
 */
class SplashInterstitial {
    private var ad: InterstitialAd? = null
    private var failed = false

    suspend fun loadAndWait(activity: Activity) {
        if (!AdsConsent.canRequestAds(activity) || !RemoteValues.isAdUnitEnabled(RemoteValues.SPLASH_INTER)) return
        val ids = RemoteValues.adUnitIds(RemoteValues.SPLASH_INTER)
        if (ids.isEmpty()) return
        load(activity, ids, 0)
        val deadline = System.currentTimeMillis() + RemoteValues.splashCounter * 1000
        while (ad == null && !failed && System.currentTimeMillis() < deadline) delay(250)
    }

    private fun load(activity: Activity, ids: List<String>, index: Int) {
        if (index >= ids.size) { failed = true; return }
        InterstitialAd.load(activity, ids[index], AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded }
            override fun onAdFailedToLoad(error: LoadAdError) { load(activity, ids, index + 1) }
        })
    }

    fun showThen(activity: Activity, next: () -> Unit) {
        val current = ad ?: return next()
        ad = null
        val overlay = AdLoadingOverlay.show(activity)
        val finish = { overlay.dismiss(); next() }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { overlay.dismiss(); AdState.fullScreenAdShowing = true }
            override fun onAdDismissedFullScreenContent() { AdState.fullScreenAdShowing = false; finish() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { AdState.fullScreenAdShowing = false; finish() }
            override fun onAdClicked() { AdState.markAdClicked() }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) { overlay.dismiss(); return@postDelayed }
            AdNavigator.markInterstitialShown(activity)
            current.show(activity)
        }, AdLoadingOverlay.SHOW_DELAY_MS)
    }
}
