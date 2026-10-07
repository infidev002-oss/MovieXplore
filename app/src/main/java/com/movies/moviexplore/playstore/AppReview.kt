package com.movies.moviexplore.playstore

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play In-App Review. Google decides whether the card is actually shown (quota), and gives no
 * result, so never tie a reward or UI change to it.
 *
 * Home Activity, first creation only:
 *   if (savedInstanceState == null) { AppReview.recordLaunch(this); inAppUpdate.check { AppReview.maybeRequest(this) } }
 * Settings "Rate us" button: AppReview.openPlayStore(context)  (the review API must not be used from a button)
 *
 * Asks at most once per process, only after MIN_LAUNCHES launches or MIN_DAYS days since install,
 * and not again for REPEAT_DAYS days.
 */
object AppReview {
    private const val PREFS = "playstore_review"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_FIRST = "first_launch_at"
    private const val KEY_LAST_ASKED = "last_asked_at"
    private const val MIN_LAUNCHES = 4
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val MIN_DAYS = 3
    private const val REPEAT_DAYS = 30

    @Volatile private var askedThisProcess = false

    fun recordLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_LAUNCHES, prefs.getInt(KEY_LAUNCHES, 0) + 1)
            if (!prefs.contains(KEY_FIRST)) putLong(KEY_FIRST, System.currentTimeMillis())
        }
    }

    private fun isEligible(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAsked = prefs.getLong(KEY_LAST_ASKED, 0L)
        if (lastAsked > 0 && now - lastAsked < REPEAT_DAYS * DAY_MS) return false
        val launches = prefs.getInt(KEY_LAUNCHES, 0)
        val first = prefs.getLong(KEY_FIRST, now)
        return launches >= MIN_LAUNCHES || now - first >= MIN_DAYS * DAY_MS
    }

    fun maybeRequest(activity: Activity) {
        if (askedThisProcess || !isAlive(activity) || !isEligible(activity)) return
        askedThisProcess = true
        val appContext = activity.applicationContext
        try {
            val manager = ReviewManagerFactory.create(appContext)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (!request.isSuccessful || !isAlive(activity)) return@addOnCompleteListener
                // Only launch over a visible screen; otherwise try again next launch.
                val resumed = (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
                if (!resumed) return@addOnCompleteListener
                try {
                    manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit { putLong(KEY_LAST_ASKED, System.currentTimeMillis()) }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** For a "Rate us" button: opens the Play Store page. */
    fun openPlayStore(context: Context) { PlayStore.open(context) }

    private fun isAlive(activity: Activity) = !activity.isFinishing && !activity.isDestroyed
}
