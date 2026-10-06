package com.movies.myapplication.ui.details

import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.model.Episode
import com.movies.myapplication.data.model.SeasonInfo
import com.movies.myapplication.mvi.MviStore
import com.movies.myapplication.ui.common.safeCall
import com.movies.myapplication.ui.common.toUserMessage
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
        reduce { copy(selected = number, isLoading = true, error = null) }
        job = scope.launch {
            safeCall { repository.episodes(tvId, number) }
                .onSuccess { episodes -> reduce { copy(episodes = episodes, isLoading = false) } }
                .onFailure { e -> reduce { copy(episodes = emptyList(), isLoading = false, error = e.toUserMessage()) } }
        }
    }
}
