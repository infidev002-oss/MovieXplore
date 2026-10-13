package com.movies.moviexplore.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.movies.moviexplore.config.RemoteValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * "Welcome back" flow: when the app returns to the foreground, opens the welcome screen
 * (WelcomeBackActivity). Its Start button shows the welcome interstitial (gle_welcome_inter_id), then
 * the user is back where they were.
 *
 * Install once in Application.onCreate():
 *   WelcomeBack.install(this, WelcomeBackActivity::class.java, SplashActivity::class.java, SignInActivity::class.java)
 *
 * Never opens: on a cold start, on rotation, over a full-screen ad, after the user tapped an ad,
 * on an excluded screen, when the ad unit is disabled / consent blocks ads, before AdRules.interAdInterval()
 * has passed since the last interstitial (AdNavigator.isInterstitialDue), or after skipNextResume()
 * (call it before sending the user out of the app: browser, share sheet, Play Store, ...).
 */
object WelcomeBack : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    enum class LoadState { IDLE, LOADING, READY, FAILED }

    private const val EXPIRY_MS = 55 * 60 * 1000L          // AdMob interstitials expire after ~1 hour
    private const val SKIP_WINDOW_MS = 10 * 60 * 1000L     // a skipNextResume() older than this is ignored

    private val main = Handler(Looper.getMainLooper())
    private var installed = false
    private var welcomeActivity: Class<out Activity>? = null
    private val excluded = mutableSetOf<String>()
    private var currentActivity: WeakReference<Activity>? = null
    private var appContext: Context? = null
    private var firstStart = true
    @Volatile private var skipRequestedAt = 0L

    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private val _state = MutableStateFlow(LoadState.IDLE)
    /** Load state of the welcome interstitial (the welcome screen waits for READY or FAILED). */
    val state: StateFlow<LoadState> = _state.asStateFlow()

    fun install(app: Application, welcomeScreen: Class<out Activity>, vararg excludedScreens: Class<out Activity>) {
        if (installed) return
        installed = true
        appContext = app.applicationContext
        welcomeActivity = welcomeScreen
        excluded += welcomeScreen.name
        excludedScreens.forEach { excluded += it.name }
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** Never open the welcome screen while one of these screens is on top. */
    fun exclude(vararg screens: Class<out Activity>) { screens.forEach { excluded += it.name } }

    /** Call right before sending the user outside the app (browser, share, Play Store, update flow). */
    fun skipNextResume() { skipRequestedAt = SystemClock.elapsedRealtime() }

    fun isEnabled(context: Context): Boolean =
        installed && AdsConsent.canRequestAds(context) &&
            RemoteValues.isAdUnitEnabled(RemoteValues.WELCOME_INTER) &&
            RemoteValues.adUnitIds(RemoteValues.WELCOME_INTER).isNotEmpty() &&
            !AdState.clickLimitReached(context)

    // ---------- app foreground / background ----------

    override fun onStart(owner: LifecycleOwner) {
        if (firstStart) { firstStart = false; return }                     // cold start: never
        val skipped = SystemClock.elapsedRealtime() - skipRequestedAt < SKIP_WINDOW_MS
        skipRequestedAt = 0L
        val clicked = AdState.consumeAdClick()
        if (skipped || clicked || AdState.fullScreenAdShowing) return
        val activity = currentActivity?.get() ?: return
        val target = welcomeActivity ?: return
        if (activity.isFinishing || activity.isDestroyed || activity.javaClass.name in excluded) return
        if (!isEnabled(activity)) return
        if (!AdNavigator.isInterstitialDue(activity)) return              // same gap as every interstitial
        try {
            activity.startActivity(Intent(activity, target))
        } catch (_: Exception) { }
    }

    override fun onStop(owner: LifecycleOwner) {
        appContext?.let { preload(it) }                                  // ready by the time the user returns
    }

    // ---------- current activity tracking ----------

    override fun onActivityPreStarted(activity: Activity) { currentActivity = WeakReference(activity) }
    override fun onActivityStarted(activity: Activity) { currentActivity = WeakReference(activity) }
    override fun onActivityResumed(activity: Activity) { currentActivity = WeakReference(activity) }
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity?.get() === activity) currentActivity = null
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    // ---------- welcome interstitial ----------

    fun preload(context: Context) {
        main.post {
            if (_state.value == LoadState.LOADING) return@post
            if (ad != null && SystemClock.elapsedRealtime() - loadedAt < EXPIRY_MS) { _state.value = LoadState.READY; return@post }
            ad = null
            if (!isEnabled(context)) { _state.value = LoadState.FAILED; return@post }
            _state.value = LoadState.LOADING
            load(context.applicationContext, RemoteValues.adUnitIds(RemoteValues.WELCOME_INTER), 0)
        }
    }

    private fun load(context: Context, ids: List<String>, index: Int) {
        if (index >= ids.size) { _state.value = LoadState.FAILED; return }
        InterstitialAd.load(context, ids[index], AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) {
                ad = loaded
                loadedAt = SystemClock.elapsedRealtime()
                _state.value = LoadState.READY
            }
            override fun onAdFailedToLoad(error: LoadAdError) { load(context, ids, index + 1) }
        })
    }

    /** Shows the welcome interstitial (after the 1 s overlay) if it's loaded and fresh, then runs next. */
    fun showThen(activity: Activity, next: () -> Unit) {
        val current = ad?.takeIf { SystemClock.elapsedRealtime() - loadedAt < EXPIRY_MS }
        if (current == null || !isEnabled(activity) || !AdNavigator.isInterstitialDue(activity) ||
            activity.isFinishing || activity.isDestroyed) { next(); return }
        InterstitialShow.run(activity, current) { end, _ ->
            when (end) {
                InterstitialShow.End.CLOSED -> {
                    if (ad === current) { ad = null; _state.value = LoadState.IDLE }
                    next()
                }
                InterstitialShow.End.BACKGROUND -> next()             // app in the background: ad kept, continue
                InterstitialShow.End.SCREEN_GONE -> Unit
            }
        }
    }
}
