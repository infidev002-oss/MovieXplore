package com.movies.moviexplore.ui.onboarding

import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.mvi.MviStore

/** The page shown and the picks on the language and country pages (saved to settings on Next). */
data class OnboardingUiState(
    val page: Int,
    val language: String,
    val country: String,
)

sealed interface OnboardingIntent {
    data class LanguagePicked(val code: String) : OnboardingIntent
    data class CountryPicked(val code: String) : OnboardingIntent

    /** Next / Start: saves this page's pick and moves on; on the last page it finishes onboarding. */
    data object NextClicked : OnboardingIntent

    /** Previous, or Back on any page but the first. */
    data object PreviousClicked : OnboardingIntent
}

sealed interface OnboardingEffect {
    /** Onboarding is done: open Home. */
    data object FinishToHome : OnboardingEffect
}

/** First run: language → country → how it works. */
class OnboardingStore(
    private val settings: SettingsRepository,
    initialState: OnboardingUiState,
) : MviStore<OnboardingUiState, OnboardingIntent, OnboardingEffect>(initialState) {

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.LanguagePicked -> reduce { copy(language = intent.code) }
            is OnboardingIntent.CountryPicked -> reduce { copy(country = intent.code) }
            OnboardingIntent.PreviousClicked -> reduce { copy(page = (page - 1).coerceAtLeast(0)) }
            OnboardingIntent.NextClicked -> when (currentState.page) {
                OnboardingActivity.PAGE_LANGUAGE -> {
                    settings.setContentLanguage(currentState.language)
                    reduce { copy(page = OnboardingActivity.PAGE_COUNTRY) }
                }
                OnboardingActivity.PAGE_COUNTRY -> {
                    settings.setCountry(currentState.country)
                    reduce { copy(page = OnboardingActivity.PAGE_TIPS) }
                }
                else -> {
                    settings.setOnboardingDone()
                    postEffect(OnboardingEffect.FinishToHome)
                }
            }
        }
    }
}
