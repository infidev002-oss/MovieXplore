package com.movies.moviexplore.ui.profile

import com.google.firebase.auth.AuthCredential
import com.movies.moviexplore.auth.AuthManager
import com.movies.moviexplore.mvi.MviStore

/** The signed-in Google user's display details; null when signed out. */
data class Account(val name: String, val email: String?)

data class ProfileUiState(
    val account: Account? = null,
    val confirmDelete: Boolean = false,
    val isDeleting: Boolean = false,
)

sealed interface ProfileIntent {
    /** Re-reads the signed-in user, e.g. when the tab becomes visible after the sign-in screen. */
    data object Refresh : ProfileIntent
    data object OpenSignIn : ProfileIntent
    data object Logout : ProfileIntent
    data object RequestDelete : ProfileIntent
    data object DismissDelete : ProfileIntent
    data object ConfirmDelete : ProfileIntent

    /** A fresh Google credential for deleting the account; null when the user closed the picker. */
    data class Reauthenticated(val credential: AuthCredential?) : ProfileIntent
}

/** Work that needs the Activity, so the screen performs it. */
sealed interface ProfileEffect {
    data object OpenSignIn : ProfileEffect

    /** Clears Credential Manager state after sign-out or account deletion. */
    data object ClearCredential : ProfileEffect

    /** Firebase needs a recent sign-in before deleting: get a fresh Google credential. */
    data object Reauthenticate : ProfileEffect
    data class Message(val text: String) : ProfileEffect
}

/** Sign-in, sign-out and account deletion all go through the kit's AuthManager. */
class ProfileStore : MviStore<ProfileUiState, ProfileIntent, ProfileEffect>(ProfileUiState(account = currentAccount())) {

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Refresh -> reduce { copy(account = currentAccount()) }
            ProfileIntent.OpenSignIn -> postEffect(ProfileEffect.OpenSignIn)
            ProfileIntent.Logout -> {
                AuthManager.signOut()
                postEffect(ProfileEffect.ClearCredential)
                reduce { copy(account = null) }
            }
            ProfileIntent.RequestDelete -> reduce { copy(confirmDelete = true) }
            ProfileIntent.DismissDelete -> reduce { copy(confirmDelete = false) }
            ProfileIntent.ConfirmDelete -> {
                reduce { copy(confirmDelete = false, isDeleting = true) }
                AuthManager.deleteAccount { error ->
                    if (error == "reauth_required") postEffect(ProfileEffect.Reauthenticate) else onDeleted(error)
                }
            }
            is ProfileIntent.Reauthenticated -> {
                val credential = intent.credential ?: return reduce { copy(isDeleting = false) }
                AuthManager.reauthenticateAndDelete(credential) { error -> onDeleted(error) }
            }
        }
    }

    private fun onDeleted(error: String?) {
        reduce { copy(account = currentAccount(), isDeleting = false) }
        if (error == null) {
            postEffect(ProfileEffect.ClearCredential)
            postEffect(ProfileEffect.Message("Your account has been deleted"))
        } else {
            postEffect(ProfileEffect.Message("Couldn't delete the account: $error"))
        }
    }

    private companion object {
        fun currentAccount(): Account? = AuthManager.currentUser()?.let { user ->
            Account(name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty(), email = user.email)
        }
    }
}
