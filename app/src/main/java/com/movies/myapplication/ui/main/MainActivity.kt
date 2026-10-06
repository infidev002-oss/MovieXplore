package com.movies.myapplication.ui.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import com.movies.myapplication.R
import com.movies.myapplication.ui.common.BaseActivity
import com.movies.myapplication.ui.common.GlassFrameLayout
import com.movies.myapplication.ui.common.bottomBarSpace
import com.movies.myapplication.ui.common.dp
import com.movies.myapplication.ui.common.onSystemBars
import com.movies.myapplication.ui.home.HomeFragment
import com.movies.myapplication.ui.mood.MoodFragment
import com.movies.myapplication.ui.profile.ProfileFragment
import com.movies.myapplication.ui.watchlist.WatchlistFragment

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

    private var current: Tab? = null
    private var bottomSpace = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val container = findViewById<FrameLayout>(R.id.tab_container)

        val navBar = findViewById<GlassFrameLayout>(R.id.nav_bar)
        navBar.enableBlur(source = container, tintedBackground = R.drawable.bg_glass_nav, border = R.drawable.bg_glass_nav_border)
        findViewById<View>(R.id.main_root).onSystemBars { bars ->
            (navBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bars.bottom + dp(12)
            navBar.requestLayout()
            bottomSpace = bottomBarSpace(bars.bottom)
            Tab.entries.forEach { fragmentFor(it)?.setBottomSpace(bottomSpace) }
        }
        Tab.entries.forEach { tab -> findViewById<View>(tab.buttonId).setOnClickListener { select(tab) } }

        val restored = savedInstanceState?.getString(KEY_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }
        select(restored ?: Tab.HOME)
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
                val fragment = tab.create().apply { setBottomSpace(bottomSpace) }
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
