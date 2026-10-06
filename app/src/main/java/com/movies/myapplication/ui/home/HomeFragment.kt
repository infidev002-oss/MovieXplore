package com.movies.myapplication.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.movies.myapplication.R
import com.movies.myapplication.appContainer
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.data.model.TmdbImage
import com.movies.myapplication.ui.common.GlassFrameLayout
import com.movies.myapplication.ui.common.ImageLoader
import com.movies.myapplication.ui.common.Navigator
import com.movies.myapplication.ui.common.addMediaRow
import com.movies.myapplication.ui.common.dp
import com.movies.myapplication.ui.common.formatRating
import com.movies.myapplication.ui.common.onSystemBars
import com.movies.myapplication.ui.common.padForSystemBars
import com.movies.myapplication.ui.common.renderState
import com.movies.myapplication.ui.common.show
import com.movies.myapplication.ui.common.showAppToast
import com.movies.myapplication.ui.common.tickHaptic
import com.movies.myapplication.ui.main.TabFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Home tab: an auto-advancing hero carousel over rows of trending, popular and top-rated titles. */
class HomeFragment : TabFragment(R.layout.tab_home) {

    private lateinit var store: HomeStore

    private lateinit var root: View
    private lateinit var scroll: ScrollView
    private lateinit var rows: LinearLayout
    private lateinit var stateView: View
    private lateinit var scrim: View
    private lateinit var hero: View
    private lateinit var heroImage: ImageView
    private lateinit var heroPrev: ImageView
    private lateinit var heroNext: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroMeta: TextView
    private lateinit var heroList: GlassFrameLayout
    private lateinit var heroListIcon: ImageView
    private lateinit var indicator: LinearLayout

    private var heroItems: List<Media> = emptyList()
    private var heroIndex = 0
    private var savedKeys: Set<String> = emptySet()
    private var renderedSections: List<HomeSection>? = null
    private var autoAdvance: Job? = null
    private var slideAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = requireContext().appContainer
        store = HomeStore(container.mediaRepository, container.watchlist)
    }

    override fun onTabViewCreated(view: View) {
        root = view
        scroll = view.findViewById(R.id.home_scroll)
        rows = view.findViewById(R.id.home_rows)
        stateView = view.findViewById(R.id.home_state)
        scrim = view.findViewById(R.id.home_status_scrim)
        hero = view.findViewById(R.id.hero)
        heroImage = view.findViewById(R.id.hero_image)
        heroPrev = view.findViewById(R.id.hero_image_prev)
        heroNext = view.findViewById(R.id.hero_image_next)
        heroTitle = view.findViewById(R.id.hero_title)
        heroMeta = view.findViewById(R.id.hero_meta)
        heroList = view.findViewById(R.id.hero_list)
        heroListIcon = view.findViewById(R.id.hero_list_icon)
        indicator = view.findViewById(R.id.hero_indicator)
        // A new view starts with an empty hero, so render() rebuilds it.
        heroItems = emptyList()
        renderedSections = null

        val activity = requireActivity()
        view.findViewById<View>(R.id.home_top_tabs).padForSystemBars(top = true)
        view.findViewById<View>(R.id.home_tab_movies).setOnClickListener { Navigator.browse(activity, MediaType.MOVIE, "Movie") }
        view.findViewById<View>(R.id.home_tab_tv).setOnClickListener { Navigator.browse(activity, MediaType.TV, "TV Shows") }
        view.findViewById<TextView>(R.id.home_tab_home).apply { paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG }

        // The hero art has its own dark top edge; the scrim only appears once rows scroll under the status bar.
        scrim.onSystemBars { bars -> scrim.layoutParams = scrim.layoutParams.apply { height = bars.top + view.dp(16) } }
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val target = if (y > hero.height - scrim.height) 1f else 0f
            if (scrim.alpha != target) scrim.animate().alpha(target).setDuration(150).start()
        }

        // The hero takes about 60% of the screen, so the first rows show without scrolling.
        val screenHeight = resources.displayMetrics.heightPixels
        hero.layoutParams = hero.layoutParams.apply { height = (screenHeight * 0.62f).toInt() }
        view.findViewById<View>(R.id.hero_fade).apply {
            layoutParams = layoutParams.apply { height = (screenHeight * 0.4f).toInt() }
        }

        view.findViewById<View>(R.id.hero_info).setOnClickListener {
            heroItems.getOrNull(heroIndex)?.let { Navigator.details(activity, it.id, it.type) }
        }
        // Frosted glass, so busy artwork behind it softens instead of clashing with the label.
        heroList.enableBlur(source = hero, tintedBackground = R.drawable.bg_button_glass_tinted, border = R.drawable.bg_button_glass_border)
        heroList.setOnClickListener {
            it.tickHaptic()
            heroItems.getOrNull(heroIndex)?.let { media ->
                val wasSaved = media.key in savedKeys
                store.onIntent(HomeIntent.ToggleSave(media))
                if (wasSaved) {
                    activity.showAppToast("Removed from My List", R.drawable.ic_bookmark)
                } else {
                    activity.showAppToast("Added to My List", R.drawable.ic_bookmark_filled)
                }
            }
        }
        enableHeroSwipe()

        viewScope.launch { store.state.collect { render(it) } }
    }

    private fun render(state: HomeUiState) {
        val firstLoad = state.hero.isEmpty()
        stateView.renderState(
            loading = state.isLoading && firstLoad,
            message = state.error.takeIf { firstLoad },
            onRetry = { store.onIntent(HomeIntent.Retry) },
        )
        scroll.show(!firstLoad)

        savedKeys = state.savedKeys
        if (state.hero != heroItems) {
            slideAnimator?.cancel()
            heroItems = state.hero
            heroIndex = 0
            buildIndicator()
            showHero()
            restartAutoAdvance()
        } else {
            updateBookmark()
        }

        if (state.sections != renderedSections) {
            renderedSections = state.sections
            rows.removeAllViews()
            val activity = requireActivity()
            state.sections.forEach { section ->
                rows.addMediaRow(
                    title = section.title,
                    items = section.items,
                    onOpen = { Navigator.details(activity, it.id, it.type) },
                    onSeeAll = { Navigator.category(activity, section.category) },
                )
            }
        }
    }

    private fun showHero() {
        val current = heroItems.getOrNull(heroIndex) ?: return
        // The neighbours preload, so the current slide comes straight from the memory cache after a swipe.
        ImageLoader.load(heroImage, TmdbImage.posterLarge(current.posterPath))
        ImageLoader.load(heroPrev, TmdbImage.posterLarge(heroItems[wrap(heroIndex - 1)].posterPath))
        ImageLoader.load(heroNext, TmdbImage.posterLarge(heroItems[wrap(heroIndex + 1)].posterPath))
        setSlideOffset(0f)
        heroTitle.text = current.title
        heroMeta.text = heroMetaLine(current)
        updateBookmark()
        for (i in 0 until indicator.childCount) styleDot(indicator.getChildAt(i), i == heroIndex)
    }

    /** "Drama · TV Show · 2026 · ★ 7.9", with the star in gold. */
    private fun heroMetaLine(media: Media): CharSequence {
        val parts = listOfNotNull(media.genre, media.type.label, media.year).joinToString("  ·  ")
        if (media.rating <= 0) return parts
        val text = SpannableStringBuilder(parts).append("  ·  ")
        val starAt = text.length
        text.append("★ ").append(formatRating(media.rating))
        text.setSpan(ForegroundColorSpan(requireContext().getColor(R.color.star)), starAt, starAt + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    /** The "My List" button shows + when the title isn't saved and ✓ when it is. */
    private fun updateBookmark() {
        val current = heroItems.getOrNull(heroIndex) ?: return
        val saved = current.key in savedKeys
        heroListIcon.setImageResource(if (saved) R.drawable.ic_check else R.drawable.ic_add)
        heroList.contentDescription = if (saved) "In My List. Remove from watchlist" else "Add to My List"
    }

    private fun buildIndicator() {
        indicator.removeAllViews()
        heroItems.indices.forEach { i ->
            val dot = View(requireContext())
            indicator.addView(dot, LinearLayout.LayoutParams(root.dp(16), root.dp(4)).apply { if (i > 0) marginStart = root.dp(6) })
            styleDot(dot, i == heroIndex)
        }
    }

    private fun styleDot(dot: View, selected: Boolean) {
        dot.layoutParams = dot.layoutParams.apply { width = root.dp(if (selected) 32 else 16) }
        dot.background = GradientDrawable().apply {
            cornerRadius = root.dp(2).toFloat()
            setColor(if (selected) requireContext().getColor(R.color.primary) else 0x998C9DB0.toInt())
        }
    }

    private fun wrap(i: Int) = (i + heroItems.size) % heroItems.size

    private fun heroWidth(): Float = (hero.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()

    /** Shifts the three slides by [dx]; the title and details fade out as the slide leaves. */
    private fun setSlideOffset(dx: Float) {
        val w = heroWidth()
        heroImage.translationX = dx
        heroPrev.translationX = dx - w
        heroNext.translationX = dx + w
        val textAlpha = (1f - abs(dx) / (w / 2f)).coerceIn(0f, 1f)
        heroTitle.alpha = textAlpha
        heroMeta.alpha = textAlpha
    }

    /** Animates from [fromDx] to the slide [step] away (0 snaps back), then makes it the current one. */
    private fun slideTo(step: Int, fromDx: Float = 0f) {
        slideAnimator?.cancel()
        slideAnimator = ValueAnimator.ofFloat(fromDx, -step * heroWidth()).apply {
            duration = 320
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { setSlideOffset(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var done = false
                override fun onAnimationCancel(animation: Animator) { done = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (done || step == 0) return
                    done = true
                    heroIndex = wrap(heroIndex + step)
                    showHero()
                    heroTitle.alpha = 0f
                    heroMeta.alpha = 0f
                    heroTitle.animate().alpha(1f).setDuration(200).start()
                    heroMeta.animate().alpha(1f).setDuration(200).start()
                }
            })
            start()
        }
    }

    /** Slides to the next title every 5 seconds; the timer restarts whenever the user swipes. */
    private fun restartAutoAdvance() {
        autoAdvance?.cancel()
        if (heroItems.size < 2) return
        autoAdvance = viewScope.launch {
            while (true) {
                delay(5_000)
                slideTo(1)
            }
        }
    }

    /** The artwork follows the finger; on release it snaps to the next, previous or same slide. */
    @SuppressLint("ClickableViewAccessibility")
    private fun enableHeroSwipe() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var dragging = false
        var tracker: VelocityTracker? = null

        hero.setOnTouchListener { _, event ->
            if (heroItems.size < 2) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // end() on an animator that already finished replays its end callback, so only finish a running one.
                    slideAnimator?.takeIf { it.isRunning }?.end()
                    autoAdvance?.cancel()
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(event.rawY - downY)) {
                        dragging = true
                        hero.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) setSlideOffset(dx)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    val dx = event.rawX - downX
                    if (dragging) {
                        val fling = abs(vx) > root.dp(400) && vx * dx > 0
                        val step = when {
                            event.actionMasked == MotionEvent.ACTION_CANCEL -> 0
                            fling || abs(dx) > heroWidth() / 3 -> if (dx < 0) 1 else -1
                            else -> 0
                        }
                        slideTo(step, dx)
                    }
                    restartAutoAdvance()
                }
            }
            tracker?.addMovement(event)
            true
        }
    }

    override fun onScrollToTop() = scroll.scrollTo(0, 0)

    override fun applyBottomSpace(px: Int) {
        scroll.setPadding(0, 0, 0, px)
    }

    override fun onDestroyView() {
        slideAnimator?.cancel()
        slideAnimator = null
        autoAdvance = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        store.clear()
        super.onDestroy()
    }
}
