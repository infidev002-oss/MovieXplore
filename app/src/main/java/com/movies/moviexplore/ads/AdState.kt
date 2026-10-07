package com.movies.moviexplore.ads

/**
 * Shared ad state used by WelcomeBack:
 * - a full-screen ad is on screen (never open the welcome screen over it),
 * - the user tapped an ad (returning from the browser / Play Store must not open the welcome screen).
 * The kit's ad classes update it; app code doesn't need to.
 */
object AdState {
    @Volatile var fullScreenAdShowing = false
        internal set

    @Volatile private var adClicked = false

    fun markAdClicked() { adClicked = true }

    /** Returns true once after an ad tap, then resets. */
    fun consumeAdClick(): Boolean {
        val clicked = adClicked
        adClicked = false
        return clicked
    }
}
