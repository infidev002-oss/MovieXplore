package com.movies.moviexplore.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd

/**
 * Shows an interstitial after the 1 s "Ad showing…" notice, for AdNavigator, SplashInterstitial and WelcomeBack.
 * Back closes only the notice; the ad still shows when the second is up.
 * [onEnd] runs exactly once with how it ended:
 *  - CLOSED: the ad was shown and closed, or failed to show — the ad is used up. [screen] is the screen to
 *    continue on: the one that showed the ad, else the app's current screen, else the original one.
 *  - BACKGROUND: the app was not in the foreground when the ad was due ([whenInBackground] = SKIP) — the ad is NOT
 *    used, keep it. [screen] is the screen it would have shown on.
 *  - SCREEN_GONE: the screen closed before the ad — the ad is NOT used, keep it. [screen] is the recreated screen
 *    when the screen was only recreated (rotation) but the new one can't take the ad, else null.
 * A screen recreated by a rotation during the notice is followed: the ad shows on the new instance.
 */
internal object InterstitialShow {
    enum class End { CLOSED, BACKGROUND, SCREEN_GONE }

    /** What to do when the ad is due while the app is in the background (Home pressed, phone locked). */
    enum class WhenInBackground {
        SKIP,               // don't show; End.BACKGROUND (navigation, welcome back)
        SHOW_ON_RETURN      // keep it and show it 1 s after the user comes back (splash)
    }

    private const val TAG = "Inter"
    private const val SHOW_AFTER_RETURN_MS = 1000L
    private val main = Handler(Looper.getMainLooper())

    fun run(
        activity: Activity,
        ad: InterstitialAd,
        whenInBackground: WhenInBackground = WhenInBackground.SKIP,
        onEnd: (end: End, screen: Activity?) -> Unit
    ) {
        var done = false
        var started = false
        var shownOn: Activity? = null
        lateinit var overlay: AdLoadingOverlay
        lateinit var showTask: Runnable

        fun end(kind: End, screen: Activity?) {
            if (done) return
            done = true
            main.removeCallbacks(showTask)
            overlay.dismiss()
            Log.d(TAG, "end: $kind on ${screen?.javaClass?.simpleName}")
            onEnd(kind, screen)
        }

        fun continueOn(): Activity =
            shownOn?.takeIf { it.isAlive() } ?: ScreenTracker.current() ?: activity

        fun show(target: Activity) {
            started = true
            shownOn = target
            AdNavigator.markInterstitialShown(target)
            Log.d(TAG, "show on ${target.javaClass.simpleName}")
            try { ad.show(target) } catch (e: Exception) { end(End.CLOSED, continueOn()) }
        }

        // Splash: wait until the screen is back in front, then show the ad a moment later.
        fun showOnReturn(target: Activity) {
            val owner = target as? LifecycleOwner ?: run { end(End.BACKGROUND, target); return }
            Log.d(TAG, "app in background: the ad waits for the user to come back")
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(o: LifecycleOwner) {
                    o.lifecycle.removeObserver(this)
                    main.postDelayed({
                        if (done) return@postDelayed
                        if (!target.isAlive()) end(End.SCREEN_GONE, null) else show(target)
                    }, SHOW_AFTER_RETURN_MS)
                }
                override fun onDestroy(o: LifecycleOwner) {
                    o.lifecycle.removeObserver(this)
                    end(End.SCREEN_GONE, null)
                }
            })
        }

        overlay = AdLoadingOverlay.show(activity) {
            // Back closes only the notice (the ad still shows). If show() already ran and no ad came up, carry on.
            if (started) end(End.CLOSED, continueOn()) else Log.d(TAG, "notice closed with Back, the ad still shows")
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                overlay.dismiss()
                if (!done) AdState.fullScreenAdShowing = true
            }
            override fun onAdDismissedFullScreenContent() {
                AdState.fullScreenAdShowing = false
                end(End.CLOSED, continueOn())
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "show failed: ${error.code} ${error.message}")
                AdState.fullScreenAdShowing = false
                end(End.CLOSED, continueOn())
            }
            override fun onAdClicked() { AdState.markAdClicked(activity) }
        }
        showTask = Runnable {
            if (done) return@Runnable
            var target: Activity = activity
            if (!activity.isAlive()) {
                if (!activity.isChangingConfigurations) { end(End.SCREEN_GONE, null); return@Runnable }
                // Rotation during the notice recreated the screen: continue on the new instance.
                val recreated = ScreenTracker.current()
                if (recreated == null || recreated.javaClass != activity.javaClass) {
                    end(End.SCREEN_GONE, recreated)
                    return@Runnable
                }
                Log.d(TAG, "screen recreated during the notice, continuing on the new instance")
                overlay.dismiss()
                target = recreated
            }
            if (!target.isResumed()) {
                // Home pressed / phone locked during the notice: never show an ad to an app in the background.
                overlay.dismiss()
                when (whenInBackground) {
                    WhenInBackground.SKIP -> end(End.BACKGROUND, target)
                    WhenInBackground.SHOW_ON_RETURN -> showOnReturn(target)
                }
                return@Runnable
            }
            show(target)
        }
        main.postDelayed(showTask, AdLoadingOverlay.SHOW_DELAY_MS)
    }

    private fun Activity.isResumed(): Boolean =
        (this as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
}
