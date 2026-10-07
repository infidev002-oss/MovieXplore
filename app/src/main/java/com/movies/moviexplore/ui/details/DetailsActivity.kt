package com.movies.moviexplore.ui.details

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.MediaDetails
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.GlassFrameLayout
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.addCreditsRow
import com.movies.moviexplore.ui.common.addHorizontalRow
import com.movies.moviexplore.ui.common.addMediaRow
import com.movies.moviexplore.ui.common.addSectionHeader
import com.movies.moviexplore.ui.common.addTrailersRow
import com.movies.moviexplore.ui.common.bindRating
import com.movies.moviexplore.ui.common.centerStartIcon
import com.movies.moviexplore.ui.common.dp
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

/** Episodes shown in the details screen's season row. */
private const val EPISODE_PREVIEW_COUNT = 20

/** A movie or TV show: artwork, info card, trailers, seasons and episodes, cast, and similar titles. */
class DetailsActivity : BaseActivity() {

    private lateinit var store: DetailsStore
    private lateinit var watchSheet: WatchAvailabilitySheet

    private var rendered: MediaDetails? = null
    private var seasonPills = mapOf<Int, TextView>()
    private var episodesRow: LinearLayout? = null
    private var episodesLoading: ProgressBar? = null
    private var renderedEpisodes: List<Episode>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        bindBanner(this, RemoteValues.BANNER_DETAILS, findViewById(R.id.banner_root), content = findViewById(R.id.details_scroll))
        val id = intent.getIntExtra(Navigator.EXTRA_ID, 0)
        store = DetailsStore(MediaRepository.shared, SettingsRepository.get(this), id, intent.mediaType()).attach()

        findViewById<View>(R.id.floating_back).apply {
            placeBelowStatusBar()
            setOnClickListener { finish() }
        }
        // The info card is frosted glass over the artwork behind it.
        findViewById<GlassFrameLayout>(R.id.details_card)
            .enableBlur(
                source = findViewById(R.id.details_art_frame),
                tintedBackground = R.drawable.bg_glass_card,
                border = R.drawable.bg_glass_card_border,
            )
        val scroll = findViewById<ScrollView>(R.id.details_scroll)
        val scrim = findViewById<View>(R.id.details_status_scrim)
        scrim.onSystemBars { bars -> scrim.layoutParams = scrim.layoutParams.apply { height = bars.top + dp(16) } }
        scroll.onSystemBars { bars -> scroll.setPadding(0, 0, 0, bars.bottom) }
        // The artwork's own top edge is fine; the scrim appears once text scrolls under the status bar.
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val target = if (y > dp(560) - scrim.height) 1f else 0f
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
        findViewById<View>(R.id.details_card).findViewById<ImageButton>(R.id.info_bookmark).apply {
            setImageResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
            contentDescription = if (saved) "Remove from watchlist" else "Add to watchlist"
        }
        renderSeasons(state)
        watchSheet.render(state.watch)
    }

    private fun bindHero(details: MediaDetails) {
        val media = details.media
        ImageLoader.load(
            findViewById<ImageView>(R.id.details_art),
            TmdbImage.posterLarge(media.posterPath) ?: TmdbImage.backdrop(media.backdropPath),
        )
        val card = findViewById<View>(R.id.details_card)
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
            sections.addSectionHeader("Choose Season") {
                val selected = store.state.value.selectedSeason ?: details.seasons.first().number
                Navigator.season(this, media.id, media.title, selected, media.backdropPath)
            }
            val pillRow = sections.addHorizontalRow()
            seasonPills = details.seasons.associate { season ->
                val pill = pillRow.inflate(R.layout.item_genre_chip) as TextView
                pill.text = season.name.ifBlank { "Season ${season.number}" }
                pill.setOnClickListener { store.onIntent(DetailsIntent.SelectSeason(season.number)) }
                pillRow.addView(pill, LinearLayout.LayoutParams(pill.layoutParams).apply { marginEnd = dp(10) })
                season.number to pill
            }
            val episodesArea = FrameLayout(this)
            sections.addView(episodesArea, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            episodesArea.minimumHeight = dp(150)
            val row = episodesArea.addHorizontalRow()
            (row.parent as View).setPadding(dp(16), dp(16), dp(4), dp(16))
            episodesRow = row
            episodesLoading = ProgressBar(this).apply { indeterminateTintList = getColorStateList(R.color.primary) }
            episodesArea.addView(episodesLoading, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
            renderedEpisodes = null
        } else {
            seasonPills = emptyMap()
            episodesRow = null
            episodesLoading = null
        }

        sections.addCreditsRow("Actors and Creators", details.credits) { Navigator.person(this, it) }
        sections.addMediaRow("Similar", details.similar, onOpen = { Navigator.details(this, it.id, it.type) })
    }

    private fun renderSeasons(state: DetailsUiState) {
        val details = state.details ?: return
        val row = episodesRow ?: return
        val selected = state.selectedSeason ?: details.seasons.firstOrNull()?.number
        seasonPills.forEach { (number, pill) -> pill.setPillSelected(number == selected) }
        episodesLoading?.show(state.episodesLoading)
        (row.parent as View).show(!state.episodesLoading)
        if (state.episodes == renderedEpisodes) return
        renderedEpisodes = state.episodes
        row.removeAllViews()
        val media = details.media
        // A preview only: some shows have 1000+ episodes in one season, and inflating them all freezes the screen.
        // "Choose Season" opens the full, recycled list.
        state.episodes.take(EPISODE_PREVIEW_COUNT).forEach { episode ->
            val card = row.inflate(R.layout.item_episode_card)
            ImageLoader.load(card.findViewById(R.id.thumbnail_image), episode.stillUrl(media.backdropPath))
            card.findViewById<TextView>(R.id.episode_title).text = "${episode.number}. ${episode.name}"
            card.findViewById<TextView>(R.id.episode_meta).text =
                listOfNotNull(formatRuntime(episode.runtime), formatDate(episode.airDate)).joinToString(" • ")
            card.setOnClickListener {
                Navigator.episode(this, media.id, media.title, selected ?: 1, episode.number, media.backdropPath)
            }
            row.addView(card)
        }
    }
}
