package com.movies.moviexplore.ads

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Fills a NativeAdView from your own layout. Pass the views your design has; missing assets are hidden.
 * The headline is required by AdMob policy; keep an "Ad" attribution label in the layout.
 */
object NativeAdBinder {
    fun bind(
        adView: NativeAdView,
        ad: NativeAd,
        headline: TextView,
        body: TextView? = null,
        callToAction: Button? = null,
        icon: ImageView? = null,
        media: MediaView? = null,
        advertiser: TextView? = null
    ) {
        headline.text = ad.headline
        adView.headlineView = headline

        body?.let { it.text = ad.body; it.visibility = if (ad.body == null) View.GONE else View.VISIBLE; adView.bodyView = it }
        callToAction?.let {
            it.text = ad.callToAction
            it.visibility = if (ad.callToAction == null) View.GONE else View.VISIBLE
            adView.callToActionView = it
        }
        icon?.let {
            it.setImageDrawable(ad.icon?.drawable)
            it.visibility = if (ad.icon == null) View.GONE else View.VISIBLE
            adView.iconView = it
        }
        advertiser?.let {
            it.text = ad.advertiser
            it.visibility = if (ad.advertiser == null) View.GONE else View.VISIBLE
            adView.advertiserView = it
        }
        media?.let { adView.mediaView = it }

        adView.setNativeAd(ad)
    }
}
