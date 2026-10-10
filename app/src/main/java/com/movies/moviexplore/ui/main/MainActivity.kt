package com.movies.moviexplore.ui.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.playstore.AppReview
import com.movies.moviexplore.playstore.InAppUpdate
import com.movies.moviexplore.ui.common.dimen
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.GlassFrameLayout
import com.movies.moviexplore.ui.common.SystemBars
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.bottomBarSpace
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.home.HomeFragment
import com.movies.moviexplore.ui.mood.MoodFragment
import com.movies.moviexplore.ui.profile.ProfileFragment
import com.movies.moviexplore.ui.watchlist.WatchlistFragment

/**
 * The app's main screen: four tab fragments (Home, Watchlist, Mood, Account) under a floating tab bar.
 * Each tab is added once and then shown or hidden, so it keeps its scroll position and loaded content.
 */
class MainActivity : BaseActivity() {

    private enum class Tab(val buttonId: Int, val create: () -> TabFragment) {
        HOME(R.id.tab_home, ::HomeFragment),
        WATCHLIST(R.id.tab_watchlist, ::WatchlistFragment),
        MOOD(R.id.tab_mood, ::MoodFragment),
        PROFILE(R.id.tab_profile, ::ProfileFragment),
    }

    private val inAppUpdate = InAppUpdate(this)   // property: registers its result launcher before onStart

    private var current: Tab? = null
    private var topSpace = 0
    private var bottomSpace = 0
    private var sideSpace = 0 to 0

    // Home's artwork runs edge to edge; each tab keeps its own text and controls clear (TabFragment.setSideSpace).
    override val padsSides = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val container = findViewById<FrameLayout>(R.id.tab_container)

        val navBar = findViewById<GlassFrameLayout>(R.id.nav_bar)
        navBar.enableBlur(source = container, tintedBackground = R.drawable.bg_glass_nav, border = R.drawable.bg_glass_nav_border)
        findViewById<View>(R.id.main_root).onSystemBars { bars ->
            (navBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bars.bottom + dimen(R.dimen.nav_bar_bottom_gap)
            navBar.requestLayout()
            bottomSpace = bottomBarSpace(bars.bottom)
            sideSpace = bars.left to bars.right
            Tab.entries.forEach { tab ->
                fragmentFor(tab)?.apply {
                    setBottomSpace(bottomSpace)
                    setSideSpace(bars.left, bars.right)
                }
            }
        }
        Tab.entries.forEach { tab -> findViewById<View>(tab.buttonId).setOnClickListener { select(tab) } }

        val banner = findViewById<View>(R.id.main_banner)
        bindBanner(this, RemoteValues.BANNER_HOME, banner, atBottom = false)
        fitBanner(banner)                    // after bindBanner, so it keeps the kit's banner spacing

        val restored = savedInstanceState?.getString(KEY_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }
        select(restored ?: Tab.HOME)

        if (savedInstanceState == null) {
            AppReview.recordLaunch(this)
            inAppUpdate.check { AppReview.maybeRequest(this) }   // review only when there is no update
        }
    }

    /**
     * The status bar space goes to the top banner while it shows (the tabs start below it), else to the tabs.
     * Re-checked on every layout, because the banner appears or hides when its ad loads or fails.
     */
    private fun fitBanner(banner: View) {
        // The kit's automatic banner spacing (set by BannerAd.load); the status bar goes above it.
        val kitTop = banner.paddingTop
        val kitBottom = banner.paddingBottom
        var bars = SystemBars(0, 0, 0, 0)
        fun apply() {
            val shown = banner.visibility == View.VISIBLE
            val top = if (shown) bars.top + kitTop else 0
            val bottom = if (shown) kitBottom else 0
            // Clear of the side navigation bar and camera cutout too (landscape): ads never sit under them.
            if (banner.paddingTop != top || banner.paddingBottom != bottom || banner.paddingLeft != bars.left || banner.paddingRight != bars.right) {
                banner.setPadding(bars.left, top, bars.right, bottom)
            }
            val tabsTop = if (shown) 0 else bars.top
            if (tabsTop != topSpace) {
                topSpace = tabsTop
                Tab.entries.forEach { fragmentFor(it)?.setTopSpace(tabsTop) }
            }
        }
        banner.onSystemBars { bars = it; apply() }
        banner.viewTreeObserver.addOnGlobalLayoutListener { apply() }
    }

    /** The tab's fragment if it has been added, including one restored by the system after process death. */
    private fun fragmentFor(tab: Tab) = supportFragmentManager.findFragmentByTag(tab.name) as? TabFragment

    /** Shows [tab], creating its fragment the first time. Switching to a tab, or tapping it again, scrolls it to the top. */
    private fun select(tab: Tab) {
        if (tab != current) {
            val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            Tab.entries.filter { it != tab }.forEach { other -> fragmentFor(other)?.let { transaction.hide(it) } }
            val existing = fragmentFor(tab)
            if (existing != null) {
                transaction.show(existing)
            } else {
                val fragment = tab.create().apply {
                    setTopSpace(topSpace)
                    setBottomSpace(bottomSpace)
                    setSideSpace(sideSpace.first, sideSpace.second)
                }
                transaction.add(R.id.tab_container, fragment, tab.name)
            }
            transaction.commitNow()
            current = tab
            styleTabBar()
        }
        fragmentFor(tab)?.scrollToTop()
        // Back from another tab returns to Home before leaving the app.
        interceptBack(if (tab == Tab.HOME) null else ({ select(Tab.HOME) }))
    }

    private fun styleTabBar() {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            findViewById<ImageButton>(tab.buttonId).apply {
                setBackgroundResource(if (selected) R.drawable.bg_nav_item_selected else R.drawable.bg_nav_item)
                setColorFilter(getColor(if (selected) R.color.background else R.color.text_primary))
                isSelected = selected
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        current?.let { outState.putString(KEY_TAB, it.name) }
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
