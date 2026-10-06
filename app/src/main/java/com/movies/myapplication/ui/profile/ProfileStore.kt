package com.movies.myapplication.ui.profile

import com.movies.myapplication.data.Account
import com.movies.myapplication.data.AccountRepository
import com.movies.myapplication.data.WatchlistRepository
import com.movies.myapplication.mvi.MviStore
import kotlinx.coroutines.launch

data class ProfileUiState(
    val account: Account? = null,
    val showLogin: Boolean = false,
    val confirmDelete: Boolean = false,
)

sealed interface ProfileIntent {
    data object OpenLogin : ProfileIntent
    data object DismissLogin : ProfileIntent
    data object ContinueAsGuest : ProfileIntent
    data object ContinueWithGoogle : ProfileIntent

    /** The phone's account picker returned; [email] is null when the user backed out. */
    data class GoogleAccountPicked(val email: String?) : ProfileIntent
    data object Logout : ProfileIntent
    data object RequestDelete : ProfileIntent
    data object DismissDelete : ProfileIntent
    data object ConfirmDelete : ProfileIntent
}

/** Work that needs the Activity, so the screen performs it. */
sealed interface ProfileEffect {
    /** Opens Android's account picker for Google accounts on this phone. */
    data object PickGoogleAccount : ProfileEffect
}

class ProfileStore(
    private val accounts: AccountRepository,
    private val watchlist: WatchlistRepository,
) : MviStore<ProfileUiState, ProfileIntent, ProfileEffect>(ProfileUiState()) {

    init {
        scope.launch { accounts.account.collect { account -> reduce { copy(account = account) } } }
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.OpenLogin -> reduce { copy(showLogin = true) }
            ProfileIntent.DismissLogin -> reduce { copy(showLogin = false) }
            ProfileIntent.ContinueAsGuest -> {
                accounts.signIn(Account(name = "Guest", email = null, isGuest = true))
                reduce { copy(showLogin = false) }
            }
            ProfileIntent.ContinueWithGoogle -> postEffect(ProfileEffect.PickGoogleAccount)
            is ProfileIntent.GoogleAccountPicked -> {
                val email = intent.email ?: return
                val name = email.substringBefore('@').replaceFirstChar { it.uppercase() }
                accounts.signIn(Account(name = name, email = email, isGuest = false))
                reduce { copy(showLogin = false) }
            }
            ProfileIntent.Logout -> accounts.signOut()
            ProfileIntent.RequestDelete -> reduce { copy(confirmDelete = true) }
            ProfileIntent.DismissDelete -> reduce { copy(confirmDelete = false) }
            ProfileIntent.ConfirmDelete -> {
                watchlist.clear()
                accounts.signOut()
                reduce { copy(confirmDelete = false) }
            }
        }
    }
}
