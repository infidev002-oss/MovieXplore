package com.movies.moviexplore.ui.signin

import com.google.firebase.auth.AuthCredential
import com.movies.moviexplore.auth.AuthManager
import com.movies.moviexplore.mvi.MviStore

data class SignInUiState(val isBusy: Boolean = false)

sealed interface SignInIntent {
    data object SignInClicked : SignInIntent

    /** The Google account picker returned; null when the user closed it or it failed. */
    data class CredentialReceived(val credential: AuthCredential?) : SignInIntent
}

/** Work that needs the Activity, so the screen performs it. */
sealed interface SignInEffect {
    /** Shows Credential Manager's Google account picker. */
    data object PickGoogleAccount : SignInEffect
    data class SignedIn(val name: String) : SignInEffect
    data class Error(val message: String) : SignInEffect
}

/** Google sign-in through the kit's AuthManager, which also starts the watchlist sync. */
class SignInStore : MviStore<SignInUiState, SignInIntent, SignInEffect>(SignInUiState()) {

    override fun onIntent(intent: SignInIntent) {
        when (intent) {
            SignInIntent.SignInClicked -> {
                if (currentState.isBusy) return
                reduce { copy(isBusy = true) }
                postEffect(SignInEffect.PickGoogleAccount)
            }
            is SignInIntent.CredentialReceived -> {
                val credential = intent.credential ?: return reduce { copy(isBusy = false) }
                AuthManager.signInWithGoogle(credential) { user, error ->
                    reduce { copy(isBusy = false) }
                    if (user != null) {
                        postEffect(SignInEffect.SignedIn(user.displayName ?: user.email.orEmpty()))
                    } else {
                        postEffect(SignInEffect.Error(error ?: "Sign-in failed. Please try again."))
                    }
                }
            }
        }
    }
}
