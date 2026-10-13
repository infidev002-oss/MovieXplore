package com.movies.moviexplore.ui.details

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.MediaDetails
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.SeasonInfo
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.ui.common.dimen
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.GlassFrameLayout
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.ItemAdapter
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.addCreditsRow
import com.movies.moviexplore.ui.common.addHorizontalRow
import com.movies.moviexplore.ui.common.lockSwipeDirection
import com.movies.moviexplore.ui.common.addMediaRow
import com.movies.moviexplore.ui.common.addSectionHeader
import com.movies.moviexplore.ui.common.addTrailersRow
import com.movies.moviexplore.ui.common.bindRating
import com.movies.moviexplore.ui.common.centerStartIcon
import com.movies.moviexplore.ui.common.formatDate
import com.movies.moviexplore.ui.common.formatRuntime
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.makeExpandable
import com.movies.moviexplore.ui.common.mediaType
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.openLink
import com.movies.moviexplore.ui.common.placeBelowStatusBar
import com.movies.moviexplore.ui.common.playOnYouTube
import com.movies.moviexplore.ui.common.searchOnYouTube
import com.movies.moviexplore.ui.common.renderState
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.tickHaptic
import com.movies.moviexplore.ui.common.showAppToast

/** Episodes shown in the details screen's season row; the rest are behind "See all episodes". */
private const val EPISODE_PREVIEW_COUNT = 3

/** A movie or TV show: artwork, info card, trailers, seasons and episodes, cast, and similar titles. */
class DetailsActivity : BaseActivity() {

    private lateinit var store: DetailsStore
    private lateinit var watchSheet: WatchAvailabilitySheet

    private val artFrame by lazy { findViewById<View>(R.id.details_art_frame) }
    private val card by lazy { findViewById<GlassFrameLayout>(R.id.details_card) }

    private var rendered: MediaDetails? = null

    // TV only (null for movies): the season pills, and the selected season's episode cards with their spinner.
    private var seasonPills: ItemAdapter<Pair<SeasonInfo, Boolean>>? = null
    private var episodeCards: ItemAdapter<Episode>? = null
    private var episodesRow: RecyclerView? = null
    private var episodesLoading: ProgressBar? = null
    private var episodesSeeAll: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        bindBanner(this, RemoteValues.BANNER_INFO, findViewById(R.id.banner_root), atBottom = false, inline = true)
        val id = intent.getIntExtra(Navigator.EXTRA_ID, 0)
        store = DetailsStore(MediaRepository.shared, SettingsRepository.get(this), id, intent.mediaType()).attach()

        findViewById<View>(R.id.floating_back).apply {
            placeBelowStatusBar()
            setOnClickListener { goBack() }
        }
        // The info card is frosted glass over the artwork behind it.
        card.enableBlur(
            source = artFrame,
            tintedBackground = R.drawable.bg_glass_card,
            border = R.drawable.bg_glass_card_border,
        )
        val scroll = findViewById<ScrollView>(R.id.details_scroll)
        val scrim = findViewById<View>(R.id.details_status_scrim)
        scrim.onSystemBars { bars -> scrim.layoutParams = scrim.layoutParams.apply { height = bars.top + dimen(R.dimen.status_scrim_extra) } }
        scroll.onSystemBars { bars -> scroll.setPadding(0, 0, 0, bars.bottom) }
        // The screen handles rotation itself, so the hero is re-fitted whenever the width changes.
        artFrame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) fitHero(right - left)
        }
        // The artwork's own top edge is fine; the scrim appears once text scrolls under the status bar.
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val target = if (y > artFrame.height - scrim.height) 1f else 0f
            if (scrim.alpha != target) scrim.animate().alpha(target).setDuration(150).start()
        }

        watchSheet = WatchAvailabilitySheet(
            activity = this,
            onSelectCountry = { store.onIntent(DetailsIntent.SelectCountry(it)) },
            onRetry = { store.onIntent(DetailsIntent.RetryWatch) },
            // TMDB's per-country watch page links out to each service; fall back to the title's general watch page.
            onOpenOffer = {
                val state = store.state.value
                (state.watch.current?.link ?: state.details?.watchUrl)?.let { url -> openLink(url) }
            },
            onDismiss = { store.onIntent(DetailsIntent.CloseWatch) },
        )

        store.state.observe { render(it) }
    }

    private fun render(state: DetailsUiState) {
        val details = state.details
        findViewById<View>(R.id.details_state).renderState(
            loading = details == null && state.isLoading,
            message = state.error.takeIf { details == null },
            onRetry = { store.onIntent(DetailsIntent.Retry) },
        )
        findViewById<View>(R.id.details_scroll).show(details != null)
        if (details == null) return

        if (details != rendered) {
            rendered = details
            bindHero(details)
            buildSections(details)
        }
        val saved = details.media.key in state.savedKeys
        card.findViewById<ImageButton>(R.id.info_bookmark).apply {
            setImageResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
            contentDescription = if (saved) "Remove from watchlist" else "Add to watchlist"
        }
        renderSeasons(state)
        watchSheet.render(state.watch)
    }

    /**
     * The backdrop is wide (16:9): about that tall plus room for the status bar and Back button, but never
     * more than half the screen (landscape). The info card overlaps its lower part.
     */
    private fun fitHero(width: Int) {
        val maxHeight = resources.displayMetrics.heightPixels / 2
        val artMin = dimen(R.dimen.details_art_min)
        val artHeight = (width * 9 / 16 + dimen(R.dimen.details_art_extra)).coerceIn(artMin, maxOf(artMin, maxHeight))
        val cardTop = artHeight - dimen(R.dimen.details_card_overlap)
        val cardParams = card.layoutParams as ViewGroup.MarginLayoutParams
        if (artFrame.layoutParams.height == artHeight && cardParams.topMargin == cardTop) return
        // Posted: this runs inside a layout pass, which can't take new sizes.
        artFrame.post {
            artFrame.layoutParams = artFrame.layoutParams.apply { height = artHeight }
            findViewById<View>(R.id.details_art_fade).apply { layoutParams = layoutParams.apply { height = artHeight / 2 } }
            card.layoutParams = cardParams.apply { topMargin = cardTop }
        }
    }

    private fun bindHero(details: MediaDetails) {
        val media = details.media
        // The wide backdrop; the poster only for titles without one.
        ImageLoader.load(
            findViewById<ImageView>(R.id.details_art),
            TmdbImage.backdropLarge(media.backdropPath) ?: TmdbImage.posterLarge(media.posterPath),
        )
        card.findViewById<TextView>(R.id.info_title).text = media.title
        card.findViewById<View>(R.id.info_rating).bindRating(media.rating)
        card.findViewById<TextView>(R.id.info_meta).text =
            listOfNotNull(details.genres.firstOrNull(), details.country, media.year).joinToString("  •  ")
        val overview = card.findViewById<TextView>(R.id.info_overview)
        overview.text = media.overview
        overview.show(media.overview.isNotBlank())
        if (media.overview.isNotBlank()) overview.makeExpandable(card.findViewById(R.id.info_read_more), collapsedLines = 3)
        // "Watch Now" plays the title's trailer; without one on TMDB, it searches YouTube for it.
        card.findViewById<Button>(R.id.info_action).apply {
            text = "Watch Now"
            centerStartIcon()
            setOnClickListener {
                val trailer = details.videos.firstOrNull()
                if (trailer != null) playOnYouTube(trailer) else searchOnYouTube("${media.title} ${media.year.orEmpty()} trailer")
            }
        }
        card.findViewById<View>(R.id.info_bookmark).setOnClickListener {
            it.tickHaptic()
            val wasSaved = media.key in store.state.value.savedKeys
            store.onIntent(DetailsIntent.ToggleSave(media))
            if (wasSaved) {
                showAppToast("Removed from watchlist", R.drawable.ic_bookmark)
            } else {
                showAppToast("Added to watchlist", R.drawable.ic_bookmark_filled)
            }
        }
        card.findViewById<Button>(R.id.info_watch_availability).apply {
            centerStartIcon()
            setOnClickListener { store.onIntent(DetailsIntent.OpenWatch) }
        }
    }

    private fun buildSections(details: MediaDetails) {
        val media = details.media
        val sections = findViewById<LinearLayout>(R.id.details_sections)
        sections.removeAllViews()
        sections.addTrailersRow(details.videos)

        if (media.type == MediaType.TV && details.seasons.isNotEmpty()) {
            // The selected season's full episode list (through AdNavigator: an interstitial first when due).
            val openSeason = {
                val selected = store.state.value.selectedSeason ?: details.seasons.first().number
                Navigator.season(this, media.id, media.title, selected, media.backdropPath)
            }
            sections.addSectionHeader("Choose Season", onSeeAll = openSeason)
            seasonPills = ItemAdapter<Pair<SeasonInfo, Boolean>>(R.layout.item_genre_chip, key = { it.first.number }) {
                view, (season, isSelected) ->
                val pill = view as TextView
                pill.updateLayoutParams<RecyclerView.LayoutParams> { marginEnd = dimen(R.dimen.chip_gap) }
                pill.text = season.name.ifBlank { "Season ${season.number}" }
                pill.setPillSelected(isSelected)
                pill.setOnClickListener { store.onIntent(DetailsIntent.SelectSeason(season.number)) }
            }.also { sections.addHorizontalRow().adapter = it }

            episodeCards = ItemAdapter<Episode>(R.layout.item_episode_card, key = { it.id }) { item, episode ->
                ImageLoader.load(item.findViewById(R.id.thumbnail_image), episode.stillUrl(media.backdropPath))
                item.findViewById<TextView>(R.id.episode_title).text = "${episode.number}. ${episode.name}"
                item.findViewById<TextView>(R.id.episode_meta).text =
                    listOfNotNull(formatRuntime(episode.runtime), formatDate(episode.airDate)).joinToString(" • ")
                item.setOnClickListener {
                    val season = store.state.value.selectedSeason ?: details.seasons.first().number
                    Navigator.episode(this, media.id, media.title, season, episode.number, media.backdropPath)
                }
            }
            val episodesArea = sections.inflate(R.layout.view_episodes_row)
            sections.addView(episodesArea)
            episodesRow = episodesArea.findViewById<RecyclerView>(R.id.episodes_items).also {
                it.adapter = episodeCards
                it.lockSwipeDirection()
            }
            episodesLoading = episodesArea.findViewById(R.id.episodes_loading)
            episodesSeeAll = episodesArea.findViewById<View>(R.id.episodes_see_all).apply { setOnClickListener { openSeason() } }
        } else {
            seasonPills = null
            episodeCards = null
            episodesRow = null
            episodesLoading = null
            episodesSeeAll = null
        }

        sections.addCreditsRow("Actors and Creators", details.credits) { Navigator.person(this, it) }
        sections.addMediaRow("Similar", details.similar, onOpen = { Navigator.details(this, it.id, it.type) })
    }

    private fun renderSeasons(state: DetailsUiState) {
        val details = state.details ?: return
        val cards = episodeCards ?: return
        val selected = state.selectedSeason ?: details.seasons.firstOrNull()?.number
        seasonPills?.submit(details.seasons.map { it to (it.number == selected) })
        episodesLoading?.show(state.episodesLoading)
        episodesRow?.show(!state.episodesLoading)
        // A preview only; "See all episodes" (and "Choose Season") opens the season's full list.
        cards.submit(state.episodes.take(EPISODE_PREVIEW_COUNT))
        episodesSeeAll?.show(!state.episodesLoading && state.episodes.size > EPISODE_PREVIEW_COUNT)
    }
}
