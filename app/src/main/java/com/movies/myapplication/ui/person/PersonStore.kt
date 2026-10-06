package com.movies.myapplication.ui.person

import com.movies.myapplication.data.MediaRepository
import com.movies.myapplication.data.model.Person
import com.movies.myapplication.mvi.MviStore
import com.movies.myapplication.ui.common.safeCall
import com.movies.myapplication.ui.common.toUserMessage
import kotlinx.coroutines.launch

data class PersonUiState(val person: Person? = null, val isLoading: Boolean = true, val error: String? = null)

sealed interface PersonIntent {
    data object Retry : PersonIntent
}

class PersonStore(
    private val repository: MediaRepository,
    private val id: Int,
) : MviStore<PersonUiState, PersonIntent, Nothing>(PersonUiState()) {

    init {
        load()
    }

    override fun onIntent(intent: PersonIntent) {
        when (intent) {
            PersonIntent.Retry -> load()
        }
    }

    private fun load() {
        scope.launch {
            reduce { copy(isLoading = true, error = null) }
            safeCall { repository.person(id) }
                .onSuccess { person -> reduce { copy(person = person, isLoading = false) } }
                .onFailure { e -> reduce { copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }
}
