package your.app.ads

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import your.app.R
import your.app.config.RemoteValues
import your.app.hooks.AdRules
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Ready-made native layouts: ads_native_small / _medium / _large (+ matching loading placeholders). */
enum class NativeSize(@LayoutRes val layout: Int, @LayoutRes val placeholder: Int) {
    SMALL(R.layout.ads_native_small, R.layout.ads_native_placeholder_small),
    MEDIUM(R.layout.ads_native_medium, R.layout.ads_native_placeholder_medium),
    LARGE(R.layout.ads_native_large, R.layout.ads_native_placeholder_large);

    companion object {
        fun from(name: String?): NativeSize? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Native ads in the three kit sizes, each placement with its own Remote Config unit.
 *
 * One placement = one ad unit object in Remote Config: RemoteValues.NATIVE_OB / NATIVE_HOME / NATIVE_SETTINGS /
 * NATIVE_CATEGORY, e.g. "gle_native_settings":
 *   {"id": ["…"], "isEnabled": true, "heightCTA": 40, "layout": "small"}
 * "layout" is optional: when set it overrides the size the code asks for, so a placement's layout can be
 * changed later without an app update. "heightCTA" sets the button height (kept within 36..AdRules.nativeCtaMaxDp()).
 *
 * Screen code (Fragment shown; in an Activity pass `this` for both owners):
 *   private val settingsNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_SETTINGS) }
 *   onViewCreated: NativeAds.attach(viewLifecycleOwner, settingsNative, binding.nativeSlot, RemoteValues.NATIVE_SETTINGS, NativeSize.SMALL)
 *   onResume (when visible): settingsNative.refreshIfNeeded(RemoteValues.NATIVE_SETTINGS)
 * Several natives on one screen: one viewModel(owner, key) + one slot + one attach() per unit.
 *
 * attach() shows the placeholder while the first ad loads, the ad (and each refreshed ad) when it arrives,
 * and hides the slot when there is no ad (disabled unit, no consent, every id failed).
 * RecyclerView row (you already hold the NativeAd): NativeAds.show(slot, ad, unitKey, NativeSize.MEDIUM).
 */
object NativeAds {

    /** The NativeAdViewModel for [unitKey] on this screen (one per unit, so several natives can share a screen). */
    fun viewModel(owner: ViewModelStoreOwner, unitKey: String): NativeAdViewModel =
        ViewModelProvider(owner)["native:$unitKey", NativeAdViewModel::class.java]

    /** Size used for [unitKey]: Remote Config "layout" if set, else [default]. */
    fun sizeFor(unitKey: String, default: NativeSize): NativeSize =
        NativeSize.from(RemoteValues.nativeLayout(unitKey)) ?: default

    fun attach(
        owner: LifecycleOwner,
        viewModel: NativeAdViewModel,
        container: FrameLayout,
        unitKey: String,
        size: NativeSize = NativeSize.MEDIUM
    ) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.status, viewModel.ad) { status, ad -> status to ad }.collect { (status, ad) ->
                    when {
                        ad != null -> show(container, ad, unitKey, size)
                        status == NativeAdViewModel.Status.LOADING -> showPlaceholder(container, sizeFor(unitKey, size))
                        else -> hide(container)
                    }
                }
            }
        }
    }

    /** Inflates the size for [unitKey], binds [ad] and replaces whatever [container] shows. Same ad again = no-op. */
    fun show(container: FrameLayout, ad: NativeAd, unitKey: String, size: NativeSize = NativeSize.MEDIUM) {
        val actual = sizeFor(unitKey, size)
        if (container.getTag(R.id.ads_native_slot_ad) === ad && container.getTag(R.id.ads_native_slot_size) == actual) {
            container.visibility = View.VISIBLE
            return
        }
        val adView = try {
            LayoutInflater.from(container.context).inflate(actual.layout, container, false) as NativeAdView
        } catch (_: Exception) { hide(container); return }

        val cta = adView.findViewById<Button>(R.id.ads_native_cta)
        NativeAdBinder.bind(
            adView = adView,
            ad = ad,
            headline = adView.findViewById(R.id.ads_native_headline),
            body = adView.findViewById(R.id.ads_native_body),
            callToAction = cta,
            icon = adView.findViewById<ImageView>(R.id.ads_native_icon),
            media = adView.findViewById<MediaView>(R.id.ads_native_media),
            advertiser = adView.findViewById<TextView>(R.id.ads_native_advertiser)
        )
        cta?.let { button ->
            val dp = RemoteValues.nativeCtaHeight(unitKey).coerceIn(MIN_CTA_DP, maxOf(MIN_CTA_DP, AdRules.nativeCtaMaxDp()))
            button.layoutParams = button.layoutParams.apply { height = (dp * button.resources.displayMetrics.density).toInt() }
        }

        clear(container)
        container.addView(adView)
        container.setTag(R.id.ads_native_slot_ad, ad)
        container.setTag(R.id.ads_native_slot_size, actual)
        container.visibility = View.VISIBLE
    }

    /** Pulsing grey placeholder of the same shape as [size]. */
    fun showPlaceholder(container: FrameLayout, size: NativeSize) {
        if (container.getTag(R.id.ads_native_slot_ad) == PLACEHOLDER && container.getTag(R.id.ads_native_slot_size) == size) {
            container.visibility = View.VISIBLE
            return
        }
        clear(container)
        val view = try {
            LayoutInflater.from(container.context).inflate(size.placeholder, container, false)
        } catch (_: Exception) { return }
        val pulse = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.45f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { if (!pulse.isStarted) pulse.start() }
            override fun onViewDetachedFromWindow(v: View) { pulse.cancel() }   // no leaked animator
        })
        container.addView(view)
        container.setTag(R.id.ads_native_slot_ad, PLACEHOLDER)
        container.setTag(R.id.ads_native_slot_size, size)
        container.visibility = View.VISIBLE
    }

    fun hide(container: FrameLayout) {
        clear(container)
        container.visibility = View.GONE
    }

    // The ad itself belongs to NativeAdViewModel (it destroys it); only the views are removed here.
    private fun clear(container: FrameLayout) {
        container.removeAllViews()
        container.setTag(R.id.ads_native_slot_ad, null)
        container.setTag(R.id.ads_native_slot_size, null)
    }

    private const val MIN_CTA_DP = 36
    private const val PLACEHOLDER = "placeholder"
}
