package com.movies.moviexplore.ui.category

import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.PageResult
import com.movies.moviexplore.mvi.MviStore
import com.movies.moviexplore.ui.common.CategoryList
import com.movies.moviexplore.ui.common.CategoryRequest
import com.movies.moviexplore.ui.common.safeCall
import com.movies.moviexplore.ui.common.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class CategoryUiState(
    val items: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** Last page loaded; 0 before the first one arrives. */
    val page: Int = 0,
    val hasMore: Boolean = true,
)

sealed interface CategoryIntent {
    data object LoadMore : CategoryIntent
    data object Retry : CategoryIntent
}

class CategoryStore(
    private val repository: MediaRepository,
    private val request: CategoryRequest,
) : MviStore<CategoryUiState, CategoryIntent, Nothing>(CategoryUiState()) {

    private var job: Job? = null

    init {
        loadNextPage()
    }

    override fun onIntent(intent: CategoryIntent) {
        when (intent) {
            // A failed page is not counted as loaded, so retrying is just loading the next page again.
            CategoryIntent.LoadMore, CategoryIntent.Retry -> loadNextPage()
        }
    }

    private fun loadNextPage() {
        val current = currentState
        if (!current.hasMore || job?.isActive == true) return
        val firstPage = current.page == 0
        job = scope.launch {
            reduce { copy(isLoading = firstPage, isLoadingMore = !firstPage, error = null) }
            safeCall { fetch(current.page + 1) }
                .onSuccess { result ->
                    reduce {
                        copy(
                            items = (items + result.items).distinctBy { it.key },
                            page = result.page,
                            hasMore = result.hasMore,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { e -> reduce { copy(isLoading = false, isLoadingMore = false, error = e.toUserMessage()) } }
        }
    }

    private suspend fun fetch(page: Int): PageResult = when (request.list) {
        CategoryList.GENRE -> repository.discover(request.type, DiscoverFilter(genreIds = setOf(request.genreId)), page)
        CategoryList.TRENDING -> repository.trending(request.type, page)
        CategoryList.POPULAR -> repository.popular(request.type, page)
        CategoryList.TOP_RATED -> repository.topRated(request.type, page)
    }
}
