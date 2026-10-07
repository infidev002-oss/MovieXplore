package your.app.ads

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import your.app.R
import kotlinx.coroutines.launch

/**
 * Welcome-back screen opened by WelcomeBack when the app returns to the foreground.
 * Start is enabled once the welcome interstitial is ready (or failed), or after MAX_WAIT_MS.
 * Start shows the ad, then closes this screen; the user is back on the screen they left.
 * Back is blocked. Survives rotation (the wait deadline and a pressed Start are kept in the saved state).
 */
class WelcomeBackActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var start: MaterialButton
    private lateinit var progress: ProgressBar
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
        findViewById<TextView>(R.id.ads_welcome_subtitle).text = getString(R.string.ads_welcome_subtitle, appName)
        try {
            findViewById<ImageView>(R.id.ads_welcome_icon).setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
        } catch (_: Exception) { }

        fitHero(resources.configuration)
        start = findViewById(R.id.ads_welcome_start)
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
        val hero = findViewById<View>(R.id.ads_welcome_hero) ?: return
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        hero.layoutParams = hero.layoutParams.apply {
            height = (resources.displayMetrics.density * if (landscape) 110 else 240).toInt()
        }
    }

    private fun setReady() {
        if (isFinishing || isDestroyed || !::start.isInitialized) return
        handler.removeCallbacks(enableRunnable)
        start.isEnabled = true
        progress.visibility = View.GONE
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
