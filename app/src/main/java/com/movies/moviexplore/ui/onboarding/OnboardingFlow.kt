package com.movies.moviexplore.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.ui.main.MainActivity

/** First run: language → country → how it works → Home, as pages of [OnboardingActivity]. */
object OnboardingFlow {

    /** Where an app launch resumes onboarding: on the first page not done yet, or null when it is complete. */
    fun nextStep(context: Context): Intent? {
        val settings = SettingsRepository.get(context)
        val page = when {
            !settings.languageChosen -> OnboardingActivity.PAGE_LANGUAGE
            !settings.countryChosen -> OnboardingActivity.PAGE_COUNTRY
            !settings.onboardingDone -> OnboardingActivity.PAGE_TIPS
            else -> return null
        }
        return OnboardingActivity.intent(context, page)
    }

    /** Start: onboarding is over, Home fades in as the only screen left (Back from Home leaves the app). */
    fun finishToHome(activity: Activity) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        @Suppress("DEPRECATION")   // still the per-call way to pick this one transition on every version
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
