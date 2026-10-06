package com.movies.myapplication.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * One of the four bottom-bar tabs (Home, Watchlist, Mood, Account), hosted by MainActivity.
 *
 * Subclasses set up their views in [onTabViewCreated] and collect their MVI store in [viewScope], which
 * lives exactly as long as the view. The room to keep clear for the floating tab bar can arrive before or
 * after the view exists, so it is remembered and applied once the view is ready.
 */
abstract class TabFragment(layout: Int) : Fragment(layout) {

    /** Plain coroutine scope for the current view: started in onViewCreated, cancelled in onDestroyView. */
    protected lateinit var viewScope: CoroutineScope
        private set

    private var bottomSpace = 0

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewScope = MainScope()
        onTabViewCreated(view)
        applyBottomSpace(bottomSpace)
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

    /** Called when the user switches to this tab or taps it again. */
    fun scrollToTop() {
        if (view != null) onScrollToTop()
    }

    protected abstract fun onScrollToTop()
}
