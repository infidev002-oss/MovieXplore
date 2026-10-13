package com.movies.moviexplore.ui.signin

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.movies.moviexplore.R
import com.movies.moviexplore.auth.AuthManager
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.placeBelowStatusBar
import com.movies.moviexplore.ui.common.toast
import kotlinx.coroutines.launch

/**
 * Google sign-in screen. Renders [SignInStore]'s state and performs its effects; finishes with
 * RESULT_OK once signed in. Closing it leaves the user signed out.
 */
class SignInActivity : BaseActivity() {

    override val showAdOnBack = false   // no interstitial on the sign-in screen

    private lateinit var store: SignInStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AuthManager.isSignedIn()) return finishSignedIn()
        setContentView(R.layout.activity_sign_in)
        store = SignInStore().attach()

        val button = findViewById<Button>(R.id.sign_in_google)
        val progress = findViewById<View>(R.id.sign_in_progress)
        findViewById<View>(R.id.sign_in_close).apply {
            placeBelowStatusBar(extraTopMargin = 0)
            setOnClickListener { finish() }
        }
        button.setOnClickListener { store.onIntent(SignInIntent.SignInClicked) }

        store.state.observe { state ->
            button.isEnabled = !state.isBusy
            progress.visibility = if (state.isBusy) View.VISIBLE else View.INVISIBLE
        }
        store.effects.observe { effect ->
            when (effect) {
                SignInEffect.PickGoogleAccount -> uiScope.launch {
                    store.onIntent(SignInIntent.CredentialReceived(getGoogleCredential(this@SignInActivity)))
                }
                is SignInEffect.SignedIn -> {
                    toast("Signed in as ${effect.name}")
                    finishSignedIn()
                }
                is SignInEffect.Error -> toast(effect.message, long = true)
            }
        }
    }

    private fun finishSignedIn() {
        setResult(Activity.RESULT_OK)
        finish()
    }
}
