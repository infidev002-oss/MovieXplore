package com.movies.moviexplore.hooks

import android.app.Activity
import android.content.Intent

object Startup {
    // Keep this default exactly as is.
    @Volatile
    var task: (activity: Activity, done: (destination: Intent?) -> Unit) -> Unit =
        { _, done -> done(null) }

    fun run(activity: Activity, done: (destination: Intent?) -> Unit) = task(activity, done)
}
