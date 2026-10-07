package com.movies.moviexplore.ui.details

import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.data.savedWatchlistKeys
import com.movies.moviexplore.data.toWatchlistItem
import com.movies.moviexplore.data.model.Country
import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.MediaDetails
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.WatchAvailability
import com.movies.moviexplore.mvi.MviStore
import com.movies.moviexplore.ui.common.safeCall
import com.movies.moviexplore.ui.common.toUserMessage
import com.movies.moviexplore.watchlist.WatchlistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The "Watch Availability" panel's state; [availability] stays null until it has been loaded. */
data class WatchPanelState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val countries: List<Country> = emptyList(),
    val country: String = DiscoverFilter.DEFAULT_WATCH_REGION,
    val availability: Map<String, WatchAvailability>? = null,
) {
    val selectedCountry: Country get() = countries.firstOrNull { it.code == country } ?: Country(country, country)
    val current: WatchAvailability? get() = availability?.get(country)
}

data class DetailsUiState(
    val details: MediaDetails? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedSeason: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val episodesLoading: Boolean = false,
    val savedKeys: Set<String> = emptySet(),
    val watch: WatchPanelState = WatchPanelState(),
)

sealed interface DetailsIntent {
    data object Retry : DetailsIntent
    data class SelectSeason(val number: Int) : DetailsIntent
    data class ToggleSave(val media: Media) : DetailsIntent
    data object OpenWatch : DetailsIntent
    data object CloseWatch : DetailsIntent
    data class SelectCountry(val code: String) : DetailsIntent
    data object RetryWatch : DetailsIntent
}

class DetailsStore(
    private val repository: MediaRepository,
    private val settings: SettingsRepository,
    private val id: Int,
    private val type: MediaType,
) : MviStore<DetailsUiState, DetailsIntent, Nothing>(DetailsUiState()) {

    private var episodesJob: Job? = null
    private var watchJob: Job? = null

    init {
        scope.launch { savedWatchlistKeys().collect { keys -> reduce { copy(savedKeys = keys) } } }
        // The panel opens on the country picked last time, on any title.
        scope.launch { settings.watchCountry.collect { code -> reduce { copy(watch = watch.copy(country = code)) } } }
        load()
    }

    override fun onIntent(intent: DetailsIntent) {
        when (intent) {
            DetailsIntent.Retry -> load()
            is DetailsIntent.SelectSeason -> selectSeason(intent.number)
            is DetailsIntent.ToggleSave -> WatchlistRepository.toggle(intent.media.toWatchlistItem())
            DetailsIntent.OpenWatch -> {
                reduce { copy(watch = watch.copy(isOpen = true)) }
                if (currentState.watch.availability == null) loadWatch()
            }
            DetailsIntent.CloseWatch -> reduce { copy(watch = watch.copy(isOpen = false)) }
            is DetailsIntent.SelectCountry -> {
                reduce { copy(watch = watch.copy(country = intent.code)) }
                settings.setWatchCountry(intent.code)
            }
            DetailsIntent.RetryWatch -> loadWatch()
        }
    }

    /** One call returns every country's offers, so switching country afterwards needs no request. */
    private fun loadWatch() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            reduce { copy(watch = watch.copy(isLoading = true, error = null)) }
            safeCall {
                coroutineScope {
                    val countries = async { repository.countries() }
                    val availability = async { repository.watchAvailability(type, id) }
                    countries.await() to availability.await()
                }
            }
                .onSuccess { (countries, availability) ->
                    reduce {
                        val country = watch.country.takeIf { code -> countries.any { it.code == code } }
                            ?: DiscoverFilter.DEFAULT_WATCH_REGION
                        copy(watch = watch.copy(isLoading = false, countries = countries, availability = availability, country = country))
                    }
                }
                .onFailure { e -> reduce { copy(watch = watch.copy(isLoading = false, error = e.toUserMessage())) } }
        }
    }

    private fun load() {
        scope.launch {
            reduce { copy(isLoading = true, error = null) }
            safeCall { repository.details(type, id) }
                .onSuccess { details ->
                    reduce { copy(details = details, isLoading = false) }
                    details.seasons.firstOrNull()?.let { selectSeason(it.number) }
                }
                .onFailure { e -> reduce { copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    private fun selectSeason(number: Int) {
        episodesJob?.cancel()
        reduce { copy(selectedSeason = number, episodes = emptyList(), episodesLoading = true) }
        episodesJob = scope.launch {
            val episodes = safeCall { repository.episodes(id, number) }.getOrDefault(emptyList())
            reduce { copy(episodes = episodes, episodesLoading = false) }
        }
    }
}
