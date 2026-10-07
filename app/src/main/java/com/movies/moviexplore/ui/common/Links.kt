package com.movies.moviexplore.ui.common

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.movies.moviexplore.ads.WelcomeBack
import com.movies.moviexplore.data.model.Video

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

/**
 * Starts [intent], which leaves the app (browser, YouTube, mail, ...). Every such intent goes through
 * here so the welcome-back screen doesn't open when the user comes back. Throws if nothing can open it.
 */
fun Context.startOutside(intent: Intent) {
    WelcomeBack.skipNextResume()
    startActivity(intent)
}

/** Opens [url] in the browser, falling back to [fallbackUrl]; never crashes if nothing can open it. */
fun Context.openLink(url: String, fallbackUrl: String? = null) {
    val opened = runCatching { startOutside(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    if (!opened && fallbackUrl != null) runCatching { startOutside(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))) }
}

/** Plays [video] in the YouTube app, falling back to the browser when the app isn't installed. */
fun Context.playOnYouTube(video: Video) =
    launchInYouTube(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)), video.url)

/** Opens a YouTube search for [query] in the YouTube app, falling back to the browser. */
fun Context.searchOnYouTube(query: String) =
    launchInYouTube(
        Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, query),
        "https://www.youtube.com/results?search_query=${Uri.encode(query)}",
    )

private fun Context.launchInYouTube(appIntent: Intent, webUrl: String) {
    try {
        startOutside(appIntent.setPackage(YOUTUBE_PACKAGE))
    } catch (_: ActivityNotFoundException) {
        // No YouTube app; a missing browser must not crash the app either.
        openLink(webUrl)
    }
}
