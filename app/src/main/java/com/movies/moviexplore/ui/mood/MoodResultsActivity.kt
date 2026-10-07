package com.movies.moviexplore.ui.mood

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.Mood
import com.movies.moviexplore.data.model.toFilter
import com.movies.moviexplore.ui.browse.BrowseIntent
import com.movies.moviexplore.ui.browse.BrowseStore
import com.movies.moviexplore.ui.browse.BrowseUiState
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.PagedGrid
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.bindTopBar
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.common.show

/** Results for the selected moods, with Movies and TV Shows tabs (each uses its own TMDB genres). */
class MoodResultsActivity : BaseActivity() {

    private var type = MediaType.MOVIE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        bindBanner(this, RemoteValues.BANNER_LIST, findViewById(R.id.banner_root))
        type = savedInstanceState?.getString(KEY_TYPE)?.let(MediaType::valueOf) ?: MediaType.MOVIE

        val moods = intent.getStringExtra(Navigator.EXTRA_MOODS).orEmpty().split(',')
            .mapNotNull { name -> Mood.entries.firstOrNull { it.name == name } }
            .toSet()
        // One store per tab, so each keeps its results and paging when switching back and forth.
        val stores = MediaType.entries.associateWith { BrowseStore(MediaRepository.shared, it, moods.toFilter(it)).attach() }

        findViewById<View>(R.id.top_bar).bindTopBar(moods.joinToString(" + ") { "${it.emoji} ${it.label}" }, onBack = ::finish)
        findViewById<View>(R.id.grid_pills).show(true)
        val moviesPill = findViewById<TextView>(R.id.pill_movies)
        val tvPill = findViewById<TextView>(R.id.pill_tv)

        val gridRoot = findViewById<View>(R.id.screen_grid)
        val grid = PagedGrid(
            root = gridRoot,
            onOpen = { Navigator.details(this, it.id, it.type) },
            onRetry = { stores.getValue(type).onIntent(BrowseIntent.Retry) },
            onLoadMore = { stores.getValue(type).onIntent(BrowseIntent.LoadMore) },
        )
        gridRoot.onSystemBars { bars -> grid.setBottomSpace(bars.bottom) }

        fun render(state: BrowseUiState) = grid.render(state.items, state.isLoading, state.isLoadingMore, state.error)
        fun select(newType: MediaType) {
            type = newType
            moviesPill.setPillSelected(type == MediaType.MOVIE)
            tvPill.setPillSelected(type == MediaType.TV)
            render(stores.getValue(type).state.value)
            grid.scrollToTop()
        }
        moviesPill.setOnClickListener { select(MediaType.MOVIE) }
        tvPill.setOnClickListener { select(MediaType.TV) }
        select(type)

        stores.forEach { (storeType, store) ->
            store.state.observe { if (storeType == type) render(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TYPE, type.name)
    }

    private companion object {
        const val KEY_TYPE = "type"
    }
}
