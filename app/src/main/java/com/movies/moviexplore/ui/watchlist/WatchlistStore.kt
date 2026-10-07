package com.movies.moviexplore.ui.watchlist

import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.toMedia
import com.movies.moviexplore.mvi.MviStore
import com.movies.moviexplore.watchlist.WatchlistRepository
import kotlinx.coroutines.launch

data class WatchlistUiState(val type: MediaType = MediaType.MOVIE, val items: List<Media> = emptyList()) {
    val visible: List<Media> get() = items.filter { it.type == type }
}

sealed interface WatchlistIntent {
    data class SelectType(val type: MediaType) : WatchlistIntent
    data class Remove(val media: Media) : WatchlistIntent
}

class WatchlistStore : MviStore<WatchlistUiState, WatchlistIntent, Nothing>(WatchlistUiState()) {

    init {
        scope.launch {
            WatchlistRepository.observeAll().collect { items -> reduce { copy(items = items.mapNotNull { it.toMedia() }) } }
        }
    }

    override fun onIntent(intent: WatchlistIntent) {
        when (intent) {
            is WatchlistIntent.SelectType -> reduce { copy(type = intent.type) }
            is WatchlistIntent.Remove -> WatchlistRepository.remove(intent.media.key)
        }
    }
}
