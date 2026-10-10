package com.movies.moviexplore.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.movies.moviexplore.R
import com.movies.moviexplore.ui.common.dimen
import com.movies.moviexplore.ui.common.show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * One of the four bottom-bar tabs (Home, Watchlist, Mood, Account), hosted by MainActivity.
 *
 * Subclasses set up their views in [onTabViewCreated] and collect their MVI store in [viewScope], which
 * lives exactly as long as the view. The room to keep clear (status bar, floating tab bar, side bars) can
 * arrive before or after the view exists, so it is remembered and applied once the view is ready.
 */
abstract class TabFragment(layout: Int) : Fragment(layout) {

    /** Plain coroutine scope for the current view: started in onViewCreated, cancelled in onDestroyView. */
    protected lateinit var viewScope: CoroutineScope
        private set

    private var bottomSpace = 0
    private var topSpace = 0

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewScope = MainScope()
        onTabViewCreated(view)
        applyBottomSpace(bottomSpace)
        applyTopSpace(view, topSpace)
        applySideSpace(view, sideSpace.first, sideSpace.second)
    }

    /**
     * Room to keep clear at the top for the status bar, in px. It is 0 while MainActivity's top banner shows,
     * because the banner covers the status bar and the tab starts below it.
     */
    fun setTopSpace(px: Int) {
        topSpace = px
        view?.let { applyTopSpace(it, px) }
    }

    /** Usually: `header.padTop(px)` and `fitStatusScrim(scrim, px)`. */
    protected abstract fun applyTopSpace(view: View, px: Int)

    /** Sets the top padding to the view's own (from its layout) plus [px]. */
    protected fun View.padTop(px: Int) {
        val base = getTag(R.id.base_padding_top) as? Int ?: paddingTop.also { setTag(R.id.base_padding_top, it) }
        if (paddingTop != base + px) setPadding(paddingLeft, base + px, paddingRight, paddingBottom)
    }

    /** The shade behind the status bar for content scrolling under it; not needed while the banner covers the bar. */
    protected fun fitStatusScrim(scrim: View, px: Int) {
        scrim.show(px > 0)
        val height = px + scrim.dimen(R.dimen.status_scrim_extra)
        if (scrim.layoutParams.height != height) scrim.layoutParams = scrim.layoutParams.apply { this.height = height }
    }

    override fun onDestroyView() {
        viewScope.cancel()
        super.onDestroyView()
    }

    /** Finds the views, wires up listeners and starts collecting the store's state in [viewScope]. */
    protected abstract fun onTabViewCreated(view: View)

    /** Room to keep clear at the bottom for the floating tab bar and system navigation bar, in px. */
    fun setBottomSpace(px: Int) {
        bottomSpace = px
        if (view != null) applyBottomSpace(px)
    }

    protected abstract fun applyBottomSpace(px: Int)

    private var sideSpace = 0 to 0

    /** Room to keep clear on the left / right for the side navigation bar and camera cutout (landscape), in px. */
    fun setSideSpace(left: Int, right: Int) {
        sideSpace = left to right
        view?.let { applySideSpace(it, left, right) }
    }

    /** By default the whole tab is kept clear; a tab with edge-to-edge artwork pads only its text and controls. */
    protected open fun applySideSpace(view: View, left: Int, right: Int) {
        if (view.paddingLeft != left || view.paddingRight != right) view.setPadding(left, view.paddingTop, right, view.paddingBottom)
    }

    /** Called when the user switches to this tab or taps it again. */
    fun scrollToTop() {
        if (view != null) onScrollToTop()
    }

    protected abstract fun onScrollToTop()
}
