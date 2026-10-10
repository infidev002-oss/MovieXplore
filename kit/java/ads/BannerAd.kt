package your.app.ads

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import your.app.R
import your.app.config.RemoteValues
import java.util.UUID

/**
 * Adaptive banner with id failover, a pulsing placeholder (same height as the banner) until the ad loads,
 * and pause / resume / destroy tied to [owner] (Activity, or a Fragment's viewLifecycleOwner).
 *
 * Usage: BannerAd.load(activity, viewLifecycleOwner, container, RemoteValues.BANNER_HOME, label, root)
 *
 * Two types:
 *  - anchored (default): a banner fixed at the top or bottom of the screen.
 *  - inline: a banner inside scrolling content (e.g. the details page). Pass inline = true for such placements;
 *    AdMob requires inline adaptive banners there, not anchored ones.
 *
 * Remote Config, per banner unit (all optional):
 *  "inline": true / false — overrides the code's inline flag; "maxHeight": inline banner max height in dp (default 90).
 *  "size": "standard" (default: standard anchored adaptive, full width, ~50–65 dp on phones) or "large" (Google's
 *  large anchored adaptive, 50–150 dp) — anchored only.
 *  "collapsible": true makes it a collapsible banner — anchored only; after collapsing it shows at the requested
 *  "size" (Google intends collapsible for banners that are otherwise a smaller size);
 *  "collapsiblePosition": "top" or "bottom" (default) must match where the banner sits on screen.
 * The slot is hidden when every id fails, or when nothing arrives within LOAD_TIMEOUT_MS (a late ad still shows).
 */
class BannerAd private constructor(
    private val activity: Activity,
    private val root: View,
    private val container: FrameLayout,
    private val label: View?,
    private val ids: List<String>,
    private val collapsible: String?,
    private val large: Boolean,                      // anchored: large instead of standard
    private val inlineMaxHeightDp: Int?              // null = anchored banner
) : DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var adView: AdView? = null
    private var placeholder: View? = null
    private var index = 0
    private var loaded = false
    private var destroyed = false

    private val loadTimeout = Runnable {
        if (!loaded && !destroyed) { removePlaceholder(); root.visibility = View.GONE }
    }

    private fun start() {
        root.visibility = View.VISIBLE
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS)
        loadCurrent()
    }

    private fun loadCurrent() {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        if (index >= ids.size) { removePlaceholder(); root.visibility = View.GONE; destroyAdView(); return }
        destroyAdView()
        val size = adaptiveSize()
        if (placeholder == null) {
            // Inline banners size themselves after loading (up to maxHeight): reserve the max height meanwhile.
            val heightPx = inlineMaxHeightDp?.let { (it * activity.resources.displayMetrics.density).toInt() }
                ?: size.getHeightInPixels(activity)
            showPlaceholder(heightPx)
        }
        val view = AdView(activity).apply {
            adUnitId = ids[index]
            setAdSize(size)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                    handler.removeCallbacks(loadTimeout)
                    removePlaceholder()
                    label?.visibility = View.GONE
                    root.visibility = View.VISIBLE          // also when it arrives after the timeout
                }
                override fun onAdFailedToLoad(error: LoadAdError) { index++; loadCurrent() }
                override fun onAdClicked() { AdState.markAdClicked() }
            }
        }
        adView = view
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        label?.visibility = View.VISIBLE
        view.loadAd(request())
    }

    private fun request(): AdRequest {
        val builder = AdRequest.Builder()
        if (collapsible != null) {
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply {
                putString("collapsible", collapsible)
                putString("collapsible_request_id", UUID.randomUUID().toString())
            })
        }
        return builder.build()
    }

    private fun showPlaceholder(heightPx: Int) {
        val view = LayoutInflater.from(activity).inflate(R.layout.ads_banner_placeholder, container, false)
        val pulse = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.45f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { if (!pulse.isStarted) pulse.start() }
            override fun onViewDetachedFromWindow(v: View) { pulse.cancel() }
        })
        container.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, heightPx))
        placeholder = view
    }

    private fun removePlaceholder() {
        placeholder?.let { container.removeView(it) }
        placeholder = null
    }

    private fun adaptiveSize(): AdSize {
        val metrics = activity.resources.displayMetrics
        val widthPx = if (container.width > 0) container.width else metrics.widthPixels
        val widthDp = (widthPx / metrics.density).toInt()
        if (inlineMaxHeightDp != null) return AdSize.getInlineAdaptiveBannerAdSize(widthDp, inlineMaxHeightDp)
        // Anchored. A collapsible banner collapses to this size.
        if (large) return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
        return standardAnchoredSize(widthDp)
    }

    /**
     * Standard anchored adaptive banner (full width, ~50–65 dp on phones). The SDK marks this call deprecated in favour
     * of the large anchored size only; it still works. Kept on purpose for a shorter banner. If a future SDK removes
     * it, the build fails here — change only this function (e.g. to the large size) and nothing else.
     */
    @Suppress("DEPRECATION")
    private fun standardAnchoredSize(widthDp: Int): AdSize =
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)

    private fun destroyAdView() {
        adView?.let {
            it.adListener = object : AdListener() {}
            container.removeView(it)
            it.destroy()
        }
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
        handler.removeCallbacksAndMessages(null)
        destroyAdView()
        removePlaceholder()
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 15_000L

        /** Built-in vertical spacing per placement (top, bottom) in dp. */
        private fun defaultMargins(unitKey: String): Pair<Int, Int> = when (unitKey) {
            RemoteValues.BANNER_HOME, RemoteValues.BANNER_CAT -> 5 to 5
            RemoteValues.BANNER_INFO -> 10 to 10
            else -> 0 to 0                               // BANNER_SPLASH
        }

        /**
         * Returns null (and hides [root]) when the unit is disabled, has no ids or consent blocks ads.
         * Vertical spacing is automatic per placement (home / category 5 dp, details 10 dp, splash 0 — top and
         * bottom), added around Google's banner size (never taken from it) on [root], so it disappears together with
         * the slot when there is no ad. [marginTopDp] / [marginBottomDp] override it only if a screen really needs
         * something else. Never add banner margins in your layout.
         */
        fun load(
            activity: Activity,
            owner: LifecycleOwner,
            container: FrameLayout,
            unitKey: String,
            label: View? = null,
            root: View = container,
            inline: Boolean = false,
            marginTopDp: Int? = null,
            marginBottomDp: Int? = null
        ): BannerAd? {
            val density = activity.resources.displayMetrics.density
            val (defaultTop, defaultBottom) = defaultMargins(unitKey)
            root.setPadding(
                root.paddingLeft, ((marginTopDp ?: defaultTop) * density).toInt(),
                root.paddingRight, ((marginBottomDp ?: defaultBottom) * density).toInt()
            )
            val ids = RemoteValues.adUnitIds(unitKey)
            if (!AdsConsent.canRequestAds(activity) || !RemoteValues.isAdUnitEnabled(unitKey) || ids.isEmpty()) {
                root.visibility = View.GONE
                return null
            }
            val isInline = RemoteValues.bannerInline(unitKey) ?: inline
            return BannerAd(
                activity, root, container, label, ids,
                collapsible = if (isInline) null else RemoteValues.bannerCollapsible(unitKey),
                large = !isInline && RemoteValues.bannerLarge(unitKey),
                inlineMaxHeightDp = if (isInline) RemoteValues.bannerMaxHeight(unitKey) else null
            ).also {
                owner.lifecycle.addObserver(it)
                it.start()
            }
        }
    }
}
