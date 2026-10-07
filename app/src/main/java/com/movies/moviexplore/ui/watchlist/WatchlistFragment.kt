package com.movies.moviexplore.ui.watchlist

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.PagedGrid
import com.movies.moviexplore.ui.common.padForSystemBars
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.main.TabFragment
import kotlinx.coroutines.launch

/** Watchlist tab: saved titles, split into Movies and TV Shows. */
class WatchlistFragment : TabFragment(R.layout.tab_watchlist) {

    private lateinit var store: WatchlistStore

    private lateinit var moviesPill: TextView
    private lateinit var tvPill: TextView
    private lateinit var grid: PagedGrid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchlistStore()
    }

    override fun onTabViewCreated(view: View) {
        moviesPill = view.findViewById(R.id.watchlist_movies)
        tvPill = view.findViewById(R.id.watchlist_tv)
        grid = PagedGrid(
            root = view.findViewById(R.id.watchlist_grid),
            onOpen = { Navigator.details(requireActivity(), it.id, it.type) },
            onRetry = {},
            onRemove = { store.onIntent(WatchlistIntent.Remove(it)) },
            emptyMessage = "Nothing saved yet. Tap the bookmark on any title to add it here.",
        )

        view.findViewById<View>(R.id.watchlist_title).padForSystemBars(top = true)
        moviesPill.setOnClickListener { store.onIntent(WatchlistIntent.SelectType(MediaType.MOVIE)) }
        tvPill.setOnClickListener { store.onIntent(WatchlistIntent.SelectType(MediaType.TV)) }
        viewScope.launch { store.state.collect { render(it) } }
    }

    private fun render(state: WatchlistUiState) {
        moviesPill.setPillSelected(state.type == MediaType.MOVIE)
        tvPill.setPillSelected(state.type == MediaType.TV)
        grid.render(items = state.visible, isLoading = false)
    }

    override fun onScrollToTop() = grid.scrollToTop()

    override fun applyBottomSpace(px: Int) = grid.setBottomSpace(px)

    override fun onDestroy() {
        store.clear()
        super.onDestroy()
    }
}
