package com.movies.moviexplore.ads

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.movies.moviexplore.R
import kotlinx.coroutines.launch

/**
 * Welcome-back screen opened by WelcomeBack when the app returns to the foreground.
 * Start is enabled once the welcome interstitial is ready (or failed), or after MAX_WAIT_MS.
 * Start shows the ad, then closes this screen; the user is back on the screen they left.
 * Back is blocked. Survives rotation (the wait deadline and a pressed Start are kept in the saved state).
 *
 * Layout ads_welcome_back.xml is yours to redesign. Required: a clickable view with id ads_welcome_start.
 * Optional (used only if present): ads_welcome_icon (ImageView, gets the app icon), ads_welcome_subtitle (TextView,
 * gets "…in <app name>."), ads_welcome_progress (shown until Start is enabled), ads_welcome_hero (made shorter in landscape).
 * The screen handles rotation itself (configChanges), so one layout must work in portrait and landscape.
 */
class WelcomeBackActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var start: View
    private var progress: View? = null
    private var heroPortraitHeight = 0
    private var deadline = 0L
    private var started = false

    private val enableRunnable = Runnable { setReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        started = savedInstanceState?.getBoolean(KEY_STARTED) ?: false
        if (started) { finish(); return }        // Start was already pressed before a recreate
        setContentView(R.layout.ads_welcome_back)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        val appName = try { applicationInfo.loadLabel(packageManager).toString() } catch (_: Exception) { "" }
        findViewById<TextView?>(R.id.ads_welcome_subtitle)?.text = getString(R.string.ads_welcome_subtitle, appName)
        try {
            findViewById<ImageView?>(R.id.ads_welcome_icon)?.setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
        } catch (_: Exception) { }

        fitHero(resources.configuration)
        start = findViewById<View?>(R.id.ads_welcome_start) ?: run { finish(); return }   // required view missing
        progress = findViewById(R.id.ads_welcome_progress)
        start.setOnClickListener { onStartClicked() }

        deadline = savedInstanceState?.getLong(KEY_DEADLINE)?.takeIf { it > 0 }
            ?: (SystemClock.elapsedRealtime() + MAX_WAIT_MS)
        WelcomeBack.preload(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WelcomeBack.state.collect { state ->
                    if (state == WelcomeBack.LoadState.READY || state == WelcomeBack.LoadState.FAILED) setReady()
                }
            }
        }
        val remaining = deadline - SystemClock.elapsedRealtime()
        if (remaining <= 0) setReady() else handler.postDelayed(enableRunnable, remaining)
    }

    // The manifest handles rotation itself (configChanges), so resize the hero here to keep Start on screen.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fitHero(newConfig)
    }

    private fun fitHero(config: Configuration) {
        val hero = findViewById<View?>(R.id.ads_welcome_hero) ?: return
        if (heroPortraitHeight == 0) heroPortraitHeight = hero.layoutParams.height
        if (heroPortraitHeight <= 0) return                          // wrap/match_parent: leave it to the layout
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        hero.layoutParams = hero.layoutParams.apply {
            height = if (landscape) (heroPortraitHeight * 0.45f).toInt() else heroPortraitHeight
        }
    }

    private fun setReady() {
        if (isFinishing || isDestroyed || !::start.isInitialized) return
        handler.removeCallbacks(enableRunnable)
        start.isEnabled = true
        progress?.visibility = View.GONE
    }

    private fun onStartClicked() {
        if (started) return
        started = true
        start.isEnabled = false
        WelcomeBack.showThen(this) { if (!isFinishing && !isDestroyed) finish() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DEADLINE, deadline)
        outState.putBoolean(KEY_STARTED, started)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val MAX_WAIT_MS = 3000L
        const val KEY_DEADLINE = "ads_welcome_deadline"
        const val KEY_STARTED = "ads_welcome_started"
    }
}
