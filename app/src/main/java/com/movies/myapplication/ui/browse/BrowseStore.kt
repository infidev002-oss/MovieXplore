package com.movies.myapplication.ui.browse

import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.model.DiscoverFilter
import com.movies.myapplication.data.model.Genre
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.data.model.PageResult
import com.movies.myapplication.data.model.Provider
import com.movies.myapplication.data.model.inSearchOrder
import com.movies.myapplication.mvi.MviStore
import com.movies.myapplication.ui.common.safeCall
import com.movies.myapplication.ui.common.toUserMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

data class BrowseUiState(
    val query: String = "",
    val filter: DiscoverFilter = DiscoverFilter(),
    val genres: List<Genre> = emptyList(),
    val providers: List<Provider> = emptyList(),
    val showFilters: Boolean = false,
    val items: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** The trimmed query the current [items] were loaded for. */
    val loadedQuery: String = "",
    val page: Int = 0,
    val hasMore: Boolean = false,
)

sealed interface BrowseIntent {
    data class QueryChanged(val query: String) : BrowseIntent
    data object OpenFilters : BrowseIntent
    data object CloseFilters : BrowseIntent
    data class ApplyFilter(val filter: DiscoverFilter) : BrowseIntent
    data class RegionChanged(val region: String?) : BrowseIntent
    data object LoadMore : BrowseIntent
    data object Retry : BrowseIntent
}

sealed interface BrowseEffect {
    /** A Channels filter was applied while searching; it only works on the full list, so the search was cleared. */
    data object SearchClearedForChannel : BrowseEffect
}

/**
 * Paged results for one media type: discover when the query is empty, text search otherwise.
 * [loadGenres] fetches the genre list for screens that show genre chips or the filter panel.
 */
@OptIn(FlowPreview::class)
class BrowseStore(
    private val repository: MediaRepository,
    private val type: MediaType,
    initialFilter: DiscoverFilter,
    loadGenres: Boolean = false,
) : MviStore<BrowseUiState, BrowseIntent, BrowseEffect>(BrowseUiState(filter = initialFilter)) {

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var loadMoreJob: Job? = null
    private var providersJob: Job? = null

    init {
        // The first page reloads whenever the (debounced) query or the filter in the state changes, or on retry.
        scope.launch {
            val query = state
                .map { it.query }
                .distinctUntilChanged()
                .debounce { if (it.isBlank()) 0L else 400L }
                .map { it.trim() }
                .distinctUntilChanged()
            val filter = state.map { it.filter }.distinctUntilChanged()
            combine(query, filter, retries.onStart { emit(Unit) }) { q, f, _ -> q to f }
                .collectLatest { (q, f) -> loadFirstPage(q, f) }
        }
        if (loadGenres) {
            scope.launch {
                val genres = safeCall { repository.genres(type) }.getOrDefault(emptyList())
                reduce { copy(genres = genres) }
            }
        }
    }

    override fun onIntent(intent: BrowseIntent) {
        when (intent) {
            is BrowseIntent.QueryChanged -> reduce { copy(query = intent.query) }
            BrowseIntent.OpenFilters -> reduce { copy(showFilters = true) }
            BrowseIntent.CloseFilters -> reduce { copy(showFilters = false) }
            is BrowseIntent.ApplyFilter -> {
                // Search results don't say where a title streams, so a channel needs the full (discover) list.
                val clearSearch = intent.filter.providerId != null && currentState.query.isNotBlank()
                reduce { copy(filter = intent.filter, showFilters = false, query = if (clearSearch) "" else query) }
                if (clearSearch) postEffect(BrowseEffect.SearchClearedForChannel)
            }
            is BrowseIntent.RegionChanged -> loadProviders(intent.region)
            BrowseIntent.LoadMore -> loadMore()
            BrowseIntent.Retry -> retries.tryEmit(Unit)
        }
    }

    private fun loadProviders(region: String?) {
        providersJob?.cancel()
        providersJob = scope.launch {
            val providers = safeCall {
                repository.providers(type, region ?: DiscoverFilter.DEFAULT_WATCH_REGION)
            }.getOrDefault(emptyList())
            reduce { copy(providers = providers) }
        }
    }

    private fun loadMore() {
        val current = currentState
        if (!current.hasMore || current.isLoading || loadMoreJob?.isActive == true) return
        loadMoreJob = scope.launch {
            reduce { copy(isLoadingMore = true) }
            safeCall { fetch(current.loadedQuery, current.filter, current.page + 1) }
                .onSuccess { result ->
                    reduce {
                        copy(
                            items = ordered(current.loadedQuery, current.filter, (items + result.items).distinctBy { it.key }),
                            page = result.page,
                            hasMore = result.hasMore,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { reduce { copy(isLoadingMore = false) } }
        }
    }

    private suspend fun loadFirstPage(q: String, f: DiscoverFilter) {
        loadMoreJob?.cancel()
        reduce { copy(loadedQuery = q, isLoading = true, isLoadingMore = false, error = null) }
        safeCall { fetch(q, f, 1) }
            .onSuccess { result ->
                reduce {
                    copy(
                        items = ordered(q, f, result.items.distinctBy { it.key }),
                        page = result.page,
                        hasMore = result.hasMore,
                        isLoading = false,
                    )
                }
            }
            .onFailure { e ->
                reduce { copy(items = emptyList(), hasMore = false, isLoading = false, error = e.toUserMessage()) }
            }
    }

    private suspend fun fetch(q: String, f: DiscoverFilter, page: Int): PageResult {
        if (q.isEmpty()) return repository.discover(type, f, page)
        var result = repository.search(type, q, f, page)
        if (!f.filtersSearchLocally) return result
        // Search applies the filter per page, locally, so a page can match nothing. An empty page
        // would never grow the grid enough to trigger the next load, so read ahead a few pages instead.
        val items = result.items.toMutableList()
        var pagesRead = 1
        while (items.size < SEARCH_MIN_BATCH && result.hasMore && pagesRead < SEARCH_MAX_PAGES) {
            result = repository.search(type, q, f, result.page + 1)
            items += result.items
            pagesRead++
        }
        return result.copy(items = items)
    }

    /** Discover is already sorted by TMDB; search results are put in the filter's sort order here. */
    private fun ordered(q: String, f: DiscoverFilter, items: List<Media>) = if (q.isEmpty()) items else items.inSearchOrder(f.sort)

    private companion object {
        const val SEARCH_MIN_BATCH = 12
        const val SEARCH_MAX_PAGES = 5
    }
}
