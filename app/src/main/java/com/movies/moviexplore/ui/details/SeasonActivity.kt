package com.movies.moviexplore.ui.details

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.SeasonInfo
import com.movies.moviexplore.ui.common.dimen
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.ItemAdapter
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.bindTopBar
import com.movies.moviexplore.ui.common.formatDate
import com.movies.moviexplore.ui.common.formatRuntime
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.renderState
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.common.show

/** All seasons of a show as pills, and the selected season's episodes as a list. */
class SeasonActivity : BaseActivity() {

    private lateinit var store: SeasonStore
    private lateinit var list: RecyclerView

    private var tvId = 0
    private var showName = ""
    private var backdropPath: String? = null

    // Season pills: (season, is it the selected one).
    private val pillAdapter = ItemAdapter<Pair<SeasonInfo, Boolean>>(R.layout.item_genre_chip, key = { it.first.number }) {
        view, (season, isSelected) ->
        val pill = view as TextView
        pill.updateLayoutParams<RecyclerView.LayoutParams> { marginEnd = dimen(R.dimen.chip_gap) }
        pill.text = season.name.ifBlank { "Season ${season.number}" }
        pill.setPillSelected(isSelected)
        pill.setOnClickListener { store.onIntent(SeasonIntent.Select(season.number)) }
    }

    // Episode rows: still, name, runtime and air date.
    private val episodeAdapter = ItemAdapter<Episode>(R.layout.item_episode_row, key = { it.id }) { row, episode ->
        ImageLoader.load(row.findViewById(R.id.thumbnail_image), episode.stillUrl(backdropPath))
        row.findViewById<TextView>(R.id.episode_row_name).text = episode.name
        row.findViewById<TextView>(R.id.episode_row_runtime).apply {
            text = formatRuntime(episode.runtime)
            show(text.isNotEmpty())
        }
        row.findViewById<TextView>(R.id.episode_row_date).apply {
            text = formatDate(episode.airDate)
            show(text.isNotEmpty())
        }
        row.setOnClickListener {
            Navigator.episode(this, tvId, showName, store.state.value.selected, episode.number, backdropPath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_season)
        tvId = intent.getIntExtra(Navigator.EXTRA_ID, 0)
        showName = intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty()
        backdropPath = intent.getStringExtra(Navigator.EXTRA_BACKDROP)
        store = SeasonStore(MediaRepository.shared, tvId, intent.getIntExtra(Navigator.EXTRA_SEASON, 1)).attach()

        findViewById<View>(R.id.top_bar).bindTopBar(showName, onBack = ::goBack)
        findViewById<RecyclerView>(R.id.season_pills).adapter = pillAdapter
        list = findViewById(R.id.season_episodes)
        list.adapter = episodeAdapter
        list.onSystemBars { bars -> list.setPadding(list.paddingLeft, 0, list.paddingRight, dimen(R.dimen.season_list_bottom) + bars.bottom) }

        store.state.observe { state ->
            pillAdapter.submit(state.seasons.map { it to (it.number == state.selected) })
            renderEpisodes(state)
        }
    }

    /** The episode list, or loading / error / "no episodes" in its place. */
    private fun renderEpisodes(state: SeasonUiState) {
        val showList = !state.isLoading && state.error == null && state.episodes.isNotEmpty()
        findViewById<TextView>(R.id.season_count).apply {
            show(showList)
            text = "Season ${state.selected}, ${state.episodes.size} Episodes"
        }
        list.show(showList)
        episodeAdapter.submit(state.episodes)
        findViewById<View>(R.id.season_state).renderState(
            loading = state.isLoading,
            message = when {
                state.isLoading -> null
                state.error != null -> state.error
                state.episodes.isEmpty() -> "No episodes announced yet."
                else -> null
            },
            onRetry = { store.onIntent(SeasonIntent.Retry) }.takeIf { state.error != null },
        )
    }
}
