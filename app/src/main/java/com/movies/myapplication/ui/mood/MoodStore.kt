package com.movies.myapplication.ui.mood

import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.data.model.Mood
import com.movies.myapplication.mvi.MviStore
import com.movies.myapplication.ui.common.safeCall
import kotlinx.coroutines.launch

data class MoodUiState(
    val selected: Set<Mood> = emptySet(),
    val popularMovies: List<Media> = emptyList(),
    val popularTv: List<Media> = emptyList(),
)

sealed interface MoodIntent {
    data class ToggleMood(val mood: Mood) : MoodIntent
    data object ClearMoods : MoodIntent
    data object Apply : MoodIntent
}

sealed interface MoodEffect {
    data class OpenResults(val moods: Set<Mood>) : MoodEffect
}

class MoodStore(
    private val repository: MediaRepository,
) : MviStore<MoodUiState, MoodIntent, MoodEffect>(MoodUiState()) {

    init {
        scope.launch {
            safeCall { repository.popular(MediaType.MOVIE).items }.onSuccess { reduce { copy(popularMovies = it) } }
        }
        scope.launch {
            safeCall { repository.popular(MediaType.TV).items }.onSuccess { reduce { copy(popularTv = it) } }
        }
    }

    override fun onIntent(intent: MoodIntent) {
        when (intent) {
            is MoodIntent.ToggleMood -> reduce {
                copy(selected = if (intent.mood in selected) selected - intent.mood else selected + intent.mood)
            }
            MoodIntent.ClearMoods -> reduce { copy(selected = emptySet()) }
            MoodIntent.Apply -> {
                val selected = currentState.selected
                if (selected.isNotEmpty()) postEffect(MoodEffect.OpenResults(selected))
            }
        }
    }
}
