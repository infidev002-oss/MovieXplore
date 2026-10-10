package com.movies.moviexplore.ui.profile

import com.google.firebase.auth.AuthCredential
import com.movies.moviexplore.auth.AuthManager
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.mvi.MviStore
import com.movies.moviexplore.ui.common.safeCall
import kotlinx.coroutines.launch

/** The signed-in Google user's display details; null when signed out. */
data class Account(val name: String, val email: String?, val photoUrl: String?)

data class ProfileUiState(
    val account: Account? = null,
    val isSigningIn: Boolean = false,
    val confirmDelete: Boolean = false,
    val isDeleting: Boolean = false,
    /** Trending poster URLs for the sign-in panel's movie-poster backdrop; empty until loaded (or offline). */
    val posters: List<String> = emptyList(),
)

sealed interface ProfileIntent {
    /** Re-reads the signed-in user, e.g. when the tab becomes visible. */
    data object Refresh : ProfileIntent

    /** The Sign in with Google button: signs in right on this tab. */
    data object SignInClicked : ProfileIntent

    /** The Google account picker returned; null when the user closed it or it failed. */
    data class SignInCredential(val credential: AuthCredential?) : ProfileIntent
    data object Logout : ProfileIntent
    data object RequestDelete : ProfileIntent
    data object DismissDelete : ProfileIntent
    data object ConfirmDelete : ProfileIntent

    /** A fresh Google credential for deleting the account; null when the user closed the picker. */
    data class Reauthenticated(val credential: AuthCredential?) : ProfileIntent
}

/** Work that needs the Activity, so the screen performs it. */
sealed interface ProfileEffect {
    /** Shows Credential Manager's Google account picker for signing in. */
    data object PickGoogleAccount : ProfileEffect

    /** Clears Credential Manager state after sign-out or account deletion. */
    data object ClearCredential : ProfileEffect

    /** Firebase needs a recent sign-in before deleting: get a fresh Google credential. */
    data object Reauthenticate : ProfileEffect
    data class SignedIn(val name: String) : ProfileEffect

    /** The account and its data are gone; the tab is back to signed out. */
    data object AccountDeleted : ProfileEffect
    data class Message(val text: String) : ProfileEffect
}

/** Sign-in, sign-out and account deletion all go through the kit's AuthManager. */
class ProfileStore : MviStore<ProfileUiState, ProfileIntent, ProfileEffect>(ProfileUiState(account = currentAccount())) {

    init {
        // A few trending posters for the sign-in panel. Home loads the same list, so it usually comes from the cache.
        scope.launch {
            val posters = safeCall { MediaRepository.shared.trending(type = null) }.getOrNull()
                ?.items.orEmpty()
                .mapNotNull { TmdbImage.poster(it.posterPath) }
                .take(SIGN_IN_POSTERS)
            reduce { copy(posters = posters) }
        }
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Refresh -> reduce { copy(account = currentAccount()) }
            ProfileIntent.SignInClicked -> {
                if (currentState.isSigningIn) return
                reduce { copy(isSigningIn = true) }
                postEffect(ProfileEffect.PickGoogleAccount)
            }
            is ProfileIntent.SignInCredential -> {
                // The user closed the picker (or it failed): stay signed out.
                val credential = intent.credential ?: return reduce { copy(isSigningIn = false) }
                // AuthManager also writes the Profile node and starts the watchlist sync.
                AuthManager.signInWithGoogle(credential) { user, error ->
                    reduce { copy(account = currentAccount(), isSigningIn = false) }
                    if (user != null) {
                        postEffect(ProfileEffect.SignedIn(user.displayName ?: user.email.orEmpty()))
                    } else {
                        postEffect(ProfileEffect.Message(error ?: "Sign-in failed. Please try again."))
                    }
                }
            }
            ProfileIntent.Logout -> {
                AuthManager.signOut()
                postEffect(ProfileEffect.ClearCredential)
                reduce { copy(account = null) }
            }
            ProfileIntent.RequestDelete -> reduce { copy(confirmDelete = true) }
            ProfileIntent.DismissDelete -> reduce { copy(confirmDelete = false) }
            ProfileIntent.ConfirmDelete -> {
                if (currentState.isDeleting) return
                reduce { copy(confirmDelete = false, isDeleting = true) }
                AuthManager.deleteAccount { error ->
                    if (error == "reauth_required") postEffect(ProfileEffect.Reauthenticate) else onDeleted(error)
                }
            }
            is ProfileIntent.Reauthenticated -> {
                val credential = intent.credential ?: run {
                    reduce { copy(isDeleting = false) }
                    return postEffect(ProfileEffect.Message("Account not deleted. Confirm your Google account to delete it."))
                }
                AuthManager.reauthenticateAndDelete(credential) { error -> onDeleted(error) }
            }
        }
    }

    private fun onDeleted(error: String?) {
        reduce { copy(account = currentAccount(), isDeleting = false) }
        when (error) {
            null -> {
                postEffect(ProfileEffect.ClearCredential)
                postEffect(ProfileEffect.AccountDeleted)
            }
            // Already gone (e.g. signed out elsewhere): the refreshed state shows the sign-in card.
            "not_signed_in" -> postEffect(ProfileEffect.Message("You're already signed out."))
            // Firebase's own text (e.g. a different Google account picked to confirm) isn't meant for users.
            else -> postEffect(ProfileEffect.Message("Couldn't delete your account. Check your connection and try again."))
        }
    }

    private companion object {
        const val SIGN_IN_POSTERS = 4

        fun currentAccount(): Account? = AuthManager.currentUser()?.let { user ->
            Account(
                name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty(),
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
            )
        }
    }
}
