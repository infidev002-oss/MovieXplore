package com.movies.moviexplore.ui.details

import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.SeasonInfo
import com.movies.moviexplore.mvi.MviStore
import com.movies.moviexplore.ui.common.safeCall
import com.movies.moviexplore.ui.common.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SeasonUiState(
    val seasons: List<SeasonInfo> = emptyList(),
    val selected: Int,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface SeasonIntent {
    data class Select(val number: Int) : SeasonIntent
    data object Retry : SeasonIntent
}

class SeasonStore(
    private val repository: MediaRepository,
    private val tvId: Int,
    initialSeason: Int,
) : MviStore<SeasonUiState, SeasonIntent, Nothing>(SeasonUiState(selected = initialSeason)) {

    private var job: Job? = null

    init {
        scope.launch {
            safeCall { repository.seasons(tvId) }.onSuccess { seasons -> reduce { copy(seasons = seasons) } }
        }
        select(initialSeason)
    }

    override fun onIntent(intent: SeasonIntent) {
        when (intent) {
            is SeasonIntent.Select -> select(intent.number)
            SeasonIntent.Retry -> select(currentState.selected)
        }
    }

    private fun select(number: Int) {
        job?.cancel()
        // A season seen before (here or on the details page) shows at once, without a loader or a second request.
        repository.cachedEpisodes(tvId, number)?.let { cached ->
            reduce { copy(selected = number, episodes = cached, isLoading = false, error = null) }
            return
        }
        reduce { copy(selected = number, isLoading = true, error = null) }
        job = scope.launch {
            safeCall { repository.episodes(tvId, number) }
                .onSuccess { episodes -> reduce { copy(episodes = episodes, isLoading = false) } }
                .onFailure { e -> reduce { copy(episodes = emptyList(), isLoading = false, error = e.toUserMessage()) } }
        }
    }
}
