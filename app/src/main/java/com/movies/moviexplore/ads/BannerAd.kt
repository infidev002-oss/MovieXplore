package com.movies.moviexplore.ads

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.movies.moviexplore.config.RemoteValues

/**
 * Adaptive banner with id failover, an optional "Advertisement" label shown until the ad loads,
 * and pause / resume / destroy tied to [owner] (Activity, or a Fragment's viewLifecycleOwner).
 *
 * Usage: BannerAd.load(activity, viewLifecycleOwner, container, RemoteValues.BANNER_HOME, label, root)
 */
class BannerAd private constructor(
    private val activity: Activity,
    private val root: View,
    private val container: FrameLayout,
    private val label: View?,
    private val ids: List<String>
) : DefaultLifecycleObserver {

    private var adView: AdView? = null
    private var index = 0
    private var destroyed = false

    private fun loadCurrent() {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        if (index >= ids.size) { root.visibility = View.GONE; destroyAdView(); return }
        destroyAdView()
        val view = AdView(activity).apply {
            adUnitId = ids[index]
            setAdSize(adaptiveSize())
            adListener = object : AdListener() {
                override fun onAdLoaded() { label?.visibility = View.GONE }
                override fun onAdFailedToLoad(error: LoadAdError) { index++; loadCurrent() }
                override fun onAdClicked() { AdState.markAdClicked() }
            }
        }
        adView = view
        container.removeAllViews()
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.visibility = View.VISIBLE
        label?.visibility = View.VISIBLE
        view.loadAd(AdRequest.Builder().build())
    }

    private fun adaptiveSize(): AdSize {
        val metrics = activity.resources.displayMetrics
        val widthPx = if (container.width > 0) container.width else metrics.widthPixels
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, (widthPx / metrics.density).toInt())
    }

    private fun destroyAdView() {
        adView?.let { it.adListener = object : AdListener() {}; it.destroy() }
        adView = null
    }

    override fun onResume(owner: LifecycleOwner) { adView?.resume() }
    override fun onPause(owner: LifecycleOwner) { adView?.pause() }
    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
        owner.lifecycle.removeObserver(this)
    }

    fun destroy() {
        destroyed = true
        destroyAdView()
    }

    companion object {
        /** Returns null (and hides [root]) when the unit is disabled, has no ids or consent blocks ads. */
        fun load(
            activity: Activity,
            owner: LifecycleOwner,
            container: FrameLayout,
            unitKey: String,
            label: View? = null,
            root: View = container
        ): BannerAd? {
            val ids = RemoteValues.adUnitIds(unitKey)
            if (!AdsConsent.canRequestAds(activity) || !RemoteValues.isAdUnitEnabled(unitKey) || ids.isEmpty()) {
                root.visibility = View.GONE
                return null
            }
            return BannerAd(activity, root, container, label, ids).also {
                owner.lifecycle.addObserver(it)
                it.loadCurrent()
            }
        }
    }
}
