package com.movies.moviexplore

/**
 * Global AdMob switch for the whole app: the one place to turn every ad on or off.
 *
 * false = no ad is loaded or shown anywhere: banners, natives (their slots stay hidden), the splash,
 * navigation and welcome-back interstitials (the welcome-back screen doesn't open either). The app works as usual.
 * true = ads follow Remote Config as before (`ad_status`, each unit's `isEnabled`).
 *
 * It is applied once in [MovieXploreApp] through the kit's AdRules.isAdUnitEnabled hook, which every kit ad
 * class checks before loading and before showing, so any ad added later through the kit follows it too.
 */
object AdsConfig {
    const val ADS_ENABLED = true
}
