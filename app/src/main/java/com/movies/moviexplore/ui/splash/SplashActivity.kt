package com.movies.moviexplore.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.movies.moviexplore.R
import com.movies.moviexplore.splash.SplashFlow
import com.movies.moviexplore.ui.common.padForSystemBars
import com.movies.moviexplore.ui.main.MainActivity
import com.movies.moviexplore.ui.onboarding.OnboardingFlow

/** Launch screen: the design only. The kit's SplashFlow runs the whole startup sequence. */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val flow = SplashFlow(this)          // property: registers the permission launcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        findViewById<View>(R.id.splash_bottom).padForSystemBars(bottom = true)
        val status = findViewById<TextView>(R.id.splash_status)
        flow.start(
            onStatus = { state ->
                status.text = when (state) {
                    SplashFlow.Status.OFFLINE -> "No internet connection. Turn on Wi-Fi or mobile data to continue."
                    SplashFlow.Status.LOADING -> "Please wait…"
                }
            },
            bannerContainer = findViewById<FrameLayout>(R.id.splash_banner),
            onDone = { destination ->
                // A destination from the kit (e.g. a notification) always wins; then any unfinished onboarding step.
                startActivity(destination ?: OnboardingFlow.nextStep(this) ?: Intent(this, MainActivity::class.java))
                finish()
            }
        )
    }
}
