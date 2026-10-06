package com.movies.myapplication.ui.details

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.movies.myapplication.R
import com.movies.myapplication.appContainer
import com.movies.myapplication.data.model.EpisodeDetails
import com.movies.myapplication.data.model.TmdbImage
import com.movies.myapplication.ui.common.BaseActivity
import com.movies.myapplication.ui.common.ImageLoader
import com.movies.myapplication.ui.common.Navigator
import com.movies.myapplication.ui.common.addCreditsRow
import com.movies.myapplication.ui.common.addTrailersRow
import com.movies.myapplication.ui.common.bindRating
import com.movies.myapplication.ui.common.dp
import com.movies.myapplication.ui.common.formatDate
import com.movies.myapplication.ui.common.formatRuntime
import com.movies.myapplication.ui.common.makeExpandable
import com.movies.myapplication.ui.common.onSystemBars
import com.movies.myapplication.ui.common.placeBelowStatusBar
import com.movies.myapplication.ui.common.playOnYouTube
import com.movies.myapplication.ui.common.renderState
import com.movies.myapplication.ui.common.searchOnYouTube
import com.movies.myapplication.ui.common.show

/** One episode: still, name, air date and runtime, overview, trailers, and cast & crew. */
class EpisodeActivity : BaseActivity() {

    private var rendered: EpisodeDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_episode)
        val showName = intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty()
        val backdropPath = intent.getStringExtra(Navigator.EXTRA_BACKDROP)
        val store = EpisodeStore(
            repository = appContainer.mediaRepository,
            tvId = intent.getIntExtra(Navigator.EXTRA_ID, 0),
            season = intent.getIntExtra(Navigator.EXTRA_SEASON, 1),
            episode = intent.getIntExtra(Navigator.EXTRA_EPISODE, 1),
        ).attach()

        findViewById<View>(R.id.floating_back).apply {
            placeBelowStatusBar()
            setOnClickListener { finish() }
        }
        val scroll = findViewById<ScrollView>(R.id.episode_scroll)
        val scrim = findViewById<View>(R.id.episode_status_scrim)
        scrim.onSystemBars { bars -> scrim.layoutParams = scrim.layoutParams.apply { height = bars.top + dp(16) } }
        scroll.onSystemBars { bars -> scroll.setPadding(0, 0, 0, bars.bottom) }
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val art = findViewById<View>(R.id.episode_art_frame)
            val target = if (y > art.height - scrim.height) 1f else 0f
            if (scrim.alpha != target) scrim.animate().alpha(target).setDuration(150).start()
        }

        store.state.observe { state ->
            val episode = state.episode
            findViewById<View>(R.id.episode_state).renderState(
                loading = episode == null && state.isLoading,
                message = state.error.takeIf { episode == null },
                onRetry = { store.onIntent(EpisodeIntent.Retry) },
            )
            scroll.show(episode != null)
            if (episode != null && episode != rendered) {
                rendered = episode
                bind(episode, showName, backdropPath)
            }
        }
    }

    private fun bind(episode: EpisodeDetails, showName: String, backdropPath: String?) {
        ImageLoader.load(findViewById(R.id.episode_art), TmdbImage.stillLarge(episode.stillPath) ?: TmdbImage.backdrop(backdropPath))
        findViewById<TextView>(R.id.episode_show).text = "$showName  •  Season ${episode.season}, Episode ${episode.number}"
        findViewById<TextView>(R.id.episode_name).text = episode.name
        findViewById<View>(R.id.episode_rating).apply {
            show(episode.rating > 0)
            bindRating(episode.rating)
        }
        findViewById<TextView>(R.id.episode_meta).text =
            listOfNotNull(formatDate(episode.airDate), formatRuntime(episode.runtime)).joinToString("  •  ")
        val overview = findViewById<TextView>(R.id.episode_overview)
        overview.text = episode.overview
        overview.show(episode.overview.isNotBlank())
        if (episode.overview.isNotBlank()) overview.makeExpandable(findViewById(R.id.episode_read_more), collapsedLines = 5)
        // Play the episode's own video when TMDB has one; otherwise search YouTube for this episode.
        findViewById<View>(R.id.episode_watch).setOnClickListener {
            val video = episode.videos.firstOrNull()
            if (video != null) playOnYouTube(video) else searchOnYouTube("$showName S${episode.season} E${episode.number} ${episode.name}")
        }

        val sections = findViewById<LinearLayout>(R.id.episode_sections)
        sections.removeAllViews()
        sections.addTrailersRow(episode.videos)
        sections.addCreditsRow("Cast & Crew", episode.credits) { Navigator.person(this, it) }
    }
}
