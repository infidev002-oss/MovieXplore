package your.app.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import your.app.config.RemoteValues
import your.app.hooks.AdRules
import androidx.core.content.edit

/**
 * Interstitial on navigation.
 * - open(activity, intent): every ad-carrying screen change goes through this.
 * - showThen(activity) { … }: same rules for non-intent moments (e.g. switching tabs).
 * Rules: AdRules.beforeInterstitial first; then the interstitial if enabled, loaded, not expired and
 * at least AdRules.interAdInterval() ms since the last one; a 1 s "Ad showing…" overlay precedes it.
 */
object AdNavigator {
    private const val PREFS = "ads"
    private const val KEY_LAST_SHOWN = "inter_last_shown_ms"
    private const val EXPIRY_MS = 55 * 60 * 1000L     // AdMob interstitials expire after ~1 hour

    private var interstitial: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var showing = false

    /** Call after consent (splash) and after each show. Safe to call often. */
    fun preload(context: Context) {
        if (loading) return
        if (interstitial != null && System.currentTimeMillis() - loadedAt < EXPIRY_MS) return
        interstitial = null
        if (!AdsConsent.canRequestAds(context) || !RemoteValues.isAdUnitEnabled(RemoteValues.INTER)) return
        val ids = RemoteValues.adUnitIds(RemoteValues.INTER)
        if (ids.isEmpty()) return
        loading = true
        load(context.applicationContext, ids, 0)
    }

    private fun load(context: Context, ids: List<String>, index: Int) {
        if (index >= ids.size) { loading = false; return }
        InterstitialAd.load(context, ids[index], AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                loadedAt = System.currentTimeMillis()
                loading = false
            }
            override fun onAdFailedToLoad(error: LoadAdError) { load(context, ids, index + 1) }
        })
    }

    fun open(activity: Activity, intent: Intent) = showThen(activity) { activity.startActivity(intent) }

    fun showThen(activity: Activity, next: () -> Unit) {
        if (showing) return
        if (AdRules.beforeInterstitial(activity)) { next(); return }

        val ad = interstitial?.takeIf { System.currentTimeMillis() - loadedAt < EXPIRY_MS }
        if (ad == null || !isInterstitialDue(activity) || !RemoteValues.isAdUnitEnabled(RemoteValues.INTER)) {
            next()
            preload(activity)
            return
        }

        interstitial = null
        showing = true
        val overlay = AdLoadingOverlay.show(activity)
        val finish = {
            overlay.dismiss()
            showing = false
            next()
            preload(activity)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { overlay.dismiss(); AdState.fullScreenAdShowing = true }
            override fun onAdDismissedFullScreenContent() { AdState.fullScreenAdShowing = false; finish() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { AdState.fullScreenAdShowing = false; finish() }
            override fun onAdClicked() { AdState.markAdClicked() }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) { overlay.dismiss(); showing = false; return@postDelayed }
            markInterstitialShown(activity)
            ad.show(activity)
        }, AdLoadingOverlay.SHOW_DELAY_MS)
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
