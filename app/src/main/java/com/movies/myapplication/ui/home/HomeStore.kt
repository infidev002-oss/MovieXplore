package com.movies.myapplication.ui.home

import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.WatchlistRepository
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.mvi.MviStore
import com.movies.myapplication.ui.common.CategoryList
import com.movies.myapplication.ui.common.CategoryRequest
import com.movies.myapplication.ui.common.safeCall
import com.movies.myapplication.ui.common.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A Home row; its "see all" header opens [category], whose title the row shares. */
data class HomeSection(val category: CategoryRequest, val items: List<Media>) {
    val title: String get() = category.title
}

data class HomeUiState(
    val hero: List<Media> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val savedKeys: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface HomeIntent {
    data object Retry : HomeIntent
    data class ToggleSave(val media: Media) : HomeIntent
}

class HomeStore(
    private val repository: MediaRepository,
    private val watchlist: WatchlistRepository,
) : MviStore<HomeUiState, HomeIntent, Nothing>(HomeUiState()) {

    init {
        scope.launch { watchlist.keys.collect { keys -> reduce { copy(savedKeys = keys) } } }
        load()
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> load()
            is HomeIntent.ToggleSave -> watchlist.toggle(intent.media)
        }
    }

    private fun load() {
        scope.launch {
            reduce { copy(isLoading = true, error = null) }
            safeCall {
                coroutineScope {
                    val hero = async { repository.trending(null).items.take(6) }
                    val trending = async { repository.trending(MediaType.MOVIE).items }
                    val popular = async { repository.popular(MediaType.MOVIE).items }
                    val popularTv = async { repository.popular(MediaType.TV).items }
                    val topRated = async { repository.topRated(MediaType.MOVIE).items }
                    val sections = listOf(
                        HomeSection(CategoryRequest(MediaType.MOVIE, "Trending Movies", CategoryList.TRENDING), trending.await()),
                        HomeSection(CategoryRequest(MediaType.MOVIE, "Popular Movies", CategoryList.POPULAR), popular.await()),
                        HomeSection(CategoryRequest(MediaType.TV, "Popular TV Shows", CategoryList.POPULAR), popularTv.await()),
                        HomeSection(CategoryRequest(MediaType.MOVIE, "Top Rated Movies", CategoryList.TOP_RATED), topRated.await()),
                    )
                    hero.await() to sections
                }
            }
                .onSuccess { (hero, sections) -> reduce { copy(hero = hero, sections = sections, isLoading = false) } }
                .onFailure { e -> reduce { copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }
}
