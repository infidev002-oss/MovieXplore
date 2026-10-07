package com.movies.moviexplore.ui.common

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.BannerAd

/**
 * Loads the kit banner for [unitKey] into an included `view_banner` ([root]).
 *
 * A banner pinned to the bottom of the screen ([atBottom]) adds the navigation bar height below the ad.
 * When it floats over [content] (a full-screen scroll in a FrameLayout), [content] gets a bottom margin
 * equal to the banner's height while the banner shows, so the last rows stay reachable.
 */
fun Activity.bindBanner(
    owner: LifecycleOwner,
    unitKey: String,
    root: View,
    atBottom: Boolean = true,
    content: View? = null,
) {
    if (atBottom) root.onSystemBars { bars -> root.setPadding(0, 0, 0, bars.bottom) }
    if (content != null) {
        // A global listener also fires when the banner goes GONE, which a layout-change listener on it doesn't.
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val margin = if (root.visibility == View.VISIBLE) root.height else 0
            val params = content.layoutParams as ViewGroup.MarginLayoutParams
            if (params.bottomMargin != margin) {
                params.bottomMargin = margin
                content.post { content.layoutParams = params }
            }
        }
    }
    BannerAd.load(this, owner, root.findViewById<FrameLayout>(R.id.banner_container), unitKey, root.findViewById(R.id.banner_label), root)
}
