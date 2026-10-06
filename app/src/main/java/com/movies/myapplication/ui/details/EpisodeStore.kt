package com.movies.myapplication.ui.details

import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.model.EpisodeDetails
import com.movies.myapplication.mvi.MviStore
import com.movies.myapplication.ui.common.safeCall
import com.movies.myapplication.ui.common.toUserMessage
import kotlinx.coroutines.launch

data class EpisodeUiState(val episode: EpisodeDetails? = null, val isLoading: Boolean = true, val error: String? = null)

sealed interface EpisodeIntent {
    data object Retry : EpisodeIntent
}

class EpisodeStore(
    private val repository: MediaRepository,
    private val tvId: Int,
    private val season: Int,
    private val episode: Int,
) : MviStore<EpisodeUiState, EpisodeIntent, Nothing>(EpisodeUiState()) {

    init {
        load()
    }

    override fun onIntent(intent: EpisodeIntent) {
        when (intent) {
            EpisodeIntent.Retry -> load()
        }
    }

    private fun load() {
        scope.launch {
            reduce { copy(isLoading = true, error = null) }
            safeCall { repository.episode(tvId, season, episode) }
                .onSuccess { details -> reduce { copy(episode = details, isLoading = false) } }
                .onFailure { e -> reduce { copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }
}
