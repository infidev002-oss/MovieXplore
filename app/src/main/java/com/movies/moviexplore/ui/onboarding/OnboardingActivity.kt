package com.movies.moviexplore.ui.onboarding

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.movies.moviexplore.R
import com.movies.moviexplore.data.ContentLocale
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.dp
import com.movies.moviexplore.ui.common.hideKeyboard
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.padForSystemBars
import com.movies.moviexplore.ui.common.show
import kotlin.math.abs

/**
 * First-run onboarding as one screen with three pages that slide sideways: content language, country, and how
 * the app works. The step dashes and the Previous / Next buttons stay put; only the page scrolls, and only on
 * those buttons (or Back). Next saves the page's choice (used by every TMDB request, see ContentLocaleInterceptor);
 * Start on the last page finishes onboarding. Opened only through [OnboardingFlow]; no interstitials here.
 */
class OnboardingActivity : BaseActivity() {

    private class Tip(val icon: Int, val title: String, val text: String)

    private val tips = listOf(
        Tip(R.drawable.ic_search, "Discover", "Trending, popular and top-rated titles, or search any movie or show."),
        Tip(R.drawable.ic_mood, "Pick by Mood", "Choose how you feel and get matching picks."),
        Tip(R.drawable.ic_bookmark, "Save to Watchlist", "Bookmark titles. Sign in with Google to sync them."),
        Tip(R.drawable.ic_play_circle, "Trailers & Where to Watch", "Open any title for trailers, cast and streaming options."),
    )

    private lateinit var pager: ViewPager2
    private lateinit var prev: Button
    private lateinit var next: Button

    // The picks on the language and country pages; saved to settings on Next.
    private lateinit var language: String
    private lateinit var country: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        findViewById<View>(R.id.onboarding_root).padForSystemBars(top = true, bottom = true)

        // Coming back to a page shows the earlier pick; the first time, the device's language / country.
        val settings = SettingsRepository.get(this)
        language = savedInstanceState?.getString(KEY_LANGUAGE) ?: ContentLocale.language ?: ContentLocale.deviceLanguage()
        country = savedInstanceState?.getString(KEY_COUNTRY)
            ?: if (settings.countryChosen) ContentLocale.country else ContentLocale.deviceCountry()

        prev = findViewById(R.id.onboarding_prev)
        next = findViewById(R.id.onboarding_next)
        pager = findViewById(R.id.onboarding_pager)
        pager.adapter = PagesAdapter()
        pager.offscreenPageLimit = PAGES - 1        // keeps every page, with its list position and search text
        pager.isUserInputEnabled = false            // pages move on Previous / Next only
        // The page fades as it slides, so the change reads as one smooth motion.
        pager.setPageTransformer { page, position -> page.alpha = 1f - abs(position).coerceAtMost(1f) * 0.85f }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onPage(position)
        })
        val start = savedInstanceState?.getInt(KEY_PAGE) ?: intent.getIntExtra(EXTRA_PAGE, 0)
        pager.setCurrentItem(start.coerceIn(0, PAGES - 1), false)
        onPage(pager.currentItem)

        prev.setOnClickListener { goTo(pager.currentItem - 1) }
        next.setOnClickListener {
            when (pager.currentItem) {
                PAGE_LANGUAGE -> settings.setContentLanguage(language)
                PAGE_COUNTRY -> settings.setCountry(country)
                else -> {
                    settings.setOnboardingDone()
                    OnboardingFlow.finishToHome(this)
                    return@setOnClickListener
                }
            }
            goTo(pager.currentItem + 1)
        }
    }

    private fun goTo(page: Int) {
        pager.hideKeyboard()
        pager.setCurrentItem(page.coerceIn(0, PAGES - 1), true)
    }

    /** Updates everything around the page: dashes, "Step X of 3", Previous (not on the first page), Next / Start, Back. */
    private fun onPage(page: Int) {
        val dashes = findViewById<LinearLayout>(R.id.onboarding_steps)
        if (dashes.childCount == 0) repeat(PAGES) { dashes.addView(View(this)) }
        for (i in 0 until PAGES) {
            dashes.getChildAt(i).apply {
                layoutParams = LinearLayout.LayoutParams(dp(if (i == page) 32 else 16), dp(4)).apply { if (i > 0) marginStart = dp(6) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(if (i <= page) getColor(R.color.primary) else 0x598C9DB0)
                }
            }
        }
        findViewById<TextView>(R.id.onboarding_step_label).text = "Step ${page + 1} of $PAGES"
        prev.show(page > 0)
        next.text = if (page == PAGES - 1) "Start" else "Next"
        // Back steps to the previous page; on the first page it leaves the app as usual.
        interceptBack(if (page > 0) ({ goTo(page - 1) }) else null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, pager.currentItem)
        outState.putString(KEY_LANGUAGE, language)
        outState.putString(KEY_COUNTRY, country)
    }

    /** The three pages, one view type each; all stay alive (offscreenPageLimit), so each is set up once. */
    private inner class PagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = PAGES
        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val page = when (viewType) {
                PAGE_LANGUAGE -> parent.inflate(R.layout.page_onboarding_choice).also { view ->
                    view.setTitle("Choose Language", "Movie and TV titles and descriptions will appear in this language when available.")
                    ChoiceList(view, ContentChoices.languages(), language, searchable = false) { language = it }
                }
                PAGE_COUNTRY -> parent.inflate(R.layout.page_onboarding_choice).also { view ->
                    view.setTitle("Choose Country", "Popular lists, releases and where to watch will match this country.")
                    ChoiceList(view, ContentChoices.countries(), country, searchable = true) { country = it }
                }
                else -> parent.inflate(R.layout.page_onboarding_tips).also { view ->
                    view.setTitle("How It Works", "Everything you need to find your next favourite.")
                    val list = view.findViewById<LinearLayout>(R.id.onboarding_tips)
                    tips.forEach { tip ->
                        val row = list.inflate(R.layout.item_onboarding_tip)
                        row.findViewById<ImageView>(R.id.tip_icon).setImageResource(tip.icon)
                        row.findViewById<TextView>(R.id.tip_title).text = tip.title
                        row.findViewById<TextView>(R.id.tip_text).text = tip.text
                        list.addView(row)
                    }
                }
            }
            // ViewPager2 pages must fill the pager.
            page.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return object : RecyclerView.ViewHolder(page) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    private fun View.setTitle(title: String, subtitle: String) {
        findViewById<TextView>(R.id.page_title).text = title
        findViewById<TextView>(R.id.page_subtitle).text = subtitle
    }

    companion object {
        const val PAGE_LANGUAGE = 0
        const val PAGE_COUNTRY = 1
        const val PAGE_TIPS = 2
        private const val PAGES = 3

        private const val EXTRA_PAGE = "page"
        private const val KEY_PAGE = "page"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_COUNTRY = "country"

        /** Onboarding opened on [page] (one of the PAGE_ constants). */
        fun intent(context: Context, page: Int): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_PAGE, page)
    }
}
