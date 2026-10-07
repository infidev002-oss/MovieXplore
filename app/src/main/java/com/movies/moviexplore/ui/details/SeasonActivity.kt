package com.movies.moviexplore.ui.details

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.SeasonInfo
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.bindTopBar
import com.movies.moviexplore.ui.common.dp
import com.movies.moviexplore.ui.common.formatDate
import com.movies.moviexplore.ui.common.formatRuntime
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.renderState
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.common.show

/** All seasons of a show as pills, and the selected season's episodes as a list. */
class SeasonActivity : BaseActivity() {

    private var renderedSeasons: List<SeasonInfo>? = null
    private val pills = mutableMapOf<Int, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_season)
        bindBanner(this, RemoteValues.BANNER_LIST, findViewById(R.id.banner_root))
        val tvId = intent.getIntExtra(Navigator.EXTRA_ID, 0)
        val showName = intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty()
        val backdropPath = intent.getStringExtra(Navigator.EXTRA_BACKDROP)
        val store = SeasonStore(MediaRepository.shared, tvId, intent.getIntExtra(Navigator.EXTRA_SEASON, 1)).attach()

        findViewById<View>(R.id.top_bar).bindTopBar(showName, onBack = ::finish)
        val list = findViewById<ListView>(R.id.season_episodes)
        list.onSystemBars { bars -> list.setPadding(list.paddingLeft, 0, list.paddingRight, dp(24) + bars.bottom) }
        val adapter = EpisodeAdapter(backdropPath)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val episode = adapter.getItem(position)
            Navigator.episode(this, tvId, showName, store.state.value.selected, episode.number, backdropPath)
        }

        store.state.observe { state ->
            if (state.seasons != renderedSeasons) {
                renderedSeasons = state.seasons
                val row = findViewById<LinearLayout>(R.id.season_pills)
                row.removeAllViews()
                pills.clear()
                state.seasons.forEach { season ->
                    val pill = row.inflate(R.layout.item_genre_chip) as TextView
                    pill.text = season.name.ifBlank { "Season ${season.number}" }
                    pill.setOnClickListener { store.onIntent(SeasonIntent.Select(season.number)) }
                    row.addView(pill, LinearLayout.LayoutParams(pill.layoutParams).apply { marginEnd = dp(10) })
                    pills[season.number] = pill
                }
            }
            pills.forEach { (number, pill) -> pill.setPillSelected(number == state.selected) }

            val showList = !state.isLoading && state.error == null && state.episodes.isNotEmpty()
            findViewById<TextView>(R.id.season_count).apply {
                show(showList)
                text = "Season ${state.selected}, ${state.episodes.size} Episodes"
            }
            list.show(showList)
            adapter.submit(state.episodes)
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

    private class EpisodeAdapter(private val backdropPath: String?) : BaseAdapter() {
        private var episodes: List<Episode> = emptyList()

        fun submit(newEpisodes: List<Episode>) {
            if (newEpisodes == episodes) return
            episodes = newEpisodes
            notifyDataSetChanged()
        }

        override fun getCount() = episodes.size

        override fun getItem(position: Int) = episodes[position]

        override fun getItemId(position: Int) = episodes[position].id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: parent.inflate(R.layout.item_episode_row)
            val episode = episodes[position]
            ImageLoader.load(view.findViewById(R.id.thumbnail_image), episode.stillUrl(backdropPath))
            view.findViewById<TextView>(R.id.episode_row_name).text = episode.name
            view.findViewById<TextView>(R.id.episode_row_runtime).apply {
                text = formatRuntime(episode.runtime)
                show(text.isNotEmpty())
            }
            view.findViewById<TextView>(R.id.episode_row_date).apply {
                text = formatDate(episode.airDate)
                show(text.isNotEmpty())
            }
            return view
        }
    }
}
