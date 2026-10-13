package com.movies.moviexplore.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference

/**
 * Tracks the app's screen on top (never Google's ad screens), and reloads the navigation interstitial when the
 * app returns to the foreground. Installed by the kit itself (AdNavigator.preload); app code doesn't call it.
 */
internal object ScreenTracker : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var installed = false
    private var app: Application? = null
    private var current: WeakReference<Activity>? = null

    fun install(application: Application) {
        if (installed) return
        installed = true
        app = application
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** The app's screen on top, if it is still open. */
    fun current(): Activity? = current?.get()?.takeIf { it.isAlive() }

    // App back in the foreground: load the interstitial if it is missing or stale (no-op otherwise).
    override fun onStart(owner: LifecycleOwner) {
        app?.let { AdNavigator.preload(it) }
    }

    private fun track(activity: Activity) {
        if (!activity.javaClass.name.startsWith("com.google.android.gms.ads")) current = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) = track(activity)
    override fun onActivityResumed(activity: Activity) = track(activity)
    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}

internal fun Activity.isAlive() = !isFinishing && !isDestroyed
