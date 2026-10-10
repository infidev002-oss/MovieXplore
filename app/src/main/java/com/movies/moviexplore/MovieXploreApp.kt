package com.movies.moviexplore

import android.app.Application
import com.movies.moviexplore.ads.WelcomeBack
import com.movies.moviexplore.ads.WelcomeBackActivity
import com.movies.moviexplore.data.ContentLocaleInterceptor
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.hooks.NetworkHooks
import com.movies.moviexplore.network.NetworkClient
import com.movies.moviexplore.ui.onboarding.OnboardingActivity
import com.movies.moviexplore.ui.signin.SignInActivity
import com.movies.moviexplore.ui.splash.SplashActivity
import com.movies.moviexplore.watchlist.WatchlistRepository

class MovieXploreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckInit.install(this)          // kit: debug / release source sets
        NetworkClient.init(this)            // stores context only; the client itself is lazy
        // Onboarding's language and country on every TMDB request. Loaded and added before the lazy client is built.
        SettingsRepository.get(this)
        NetworkHooks.interceptors += ContentLocaleInterceptor()
        WatchlistRepository.init(this)      // resumes watchlist sync if already signed in
        // Welcome-back screen, then every screen it must never open over.
        WelcomeBack.install(this, WelcomeBackActivity::class.java, SplashActivity::class.java, OnboardingActivity::class.java, SignInActivity::class.java)
        // Nothing here may make a TMDB request or touch NetworkClient.okHttp / retrofit.
    }
}
