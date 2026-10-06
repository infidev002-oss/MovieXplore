package com.movies.myapplication.ui.watchlist

import com.movies.myapplication.data.WatchlistRepository
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.mvi.MviStore
import kotlinx.coroutines.launch

data class WatchlistUiState(val type: MediaType = MediaType.MOVIE, val items: List<Media> = emptyList()) {
    val visible: List<Media> get() = items.filter { it.type == type }
}

sealed interface WatchlistIntent {
    data class SelectType(val type: MediaType) : WatchlistIntent
    data class Remove(val media: Media) : WatchlistIntent
}

class WatchlistStore(
    private val watchlist: WatchlistRepository,
) : MviStore<WatchlistUiState, WatchlistIntent, Nothing>(WatchlistUiState()) {

    init {
        scope.launch { watchlist.items.collect { items -> reduce { copy(items = items) } } }
    }

    override fun onIntent(intent: WatchlistIntent) {
        when (intent) {
            is WatchlistIntent.SelectType -> reduce { copy(type = intent.type) }
            is WatchlistIntent.Remove -> watchlist.toggle(intent.media)
        }
    }
}
