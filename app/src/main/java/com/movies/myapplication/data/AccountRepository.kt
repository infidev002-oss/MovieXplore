package com.movies.myapplication.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in user: a Google account picked on the phone, or a guest. Only display details are kept. */
data class Account(val name: String, val email: String?, val isGuest: Boolean)

class AccountRepository(private val prefs: SharedPreferences) {

    private val _account = MutableStateFlow(read())
    val account: StateFlow<Account?> = _account.asStateFlow()

    fun signIn(account: Account) {
        _account.value = account
        prefs.edit()
            .clear()
            .putString(NAME, account.name)
            .putString(EMAIL, account.email)
            .putBoolean(GUEST, account.isGuest)
            .apply()
    }



    fun signOut() {
        _account.value = null
        prefs.edit().clear().apply()
    }

    private fun read(): Account? =
        prefs.getString(NAME, null)?.let { Account(it, prefs.getString(EMAIL, null), prefs.getBoolean(GUEST, false)) }

    private companion object {
        const val NAME = "name"
        const val EMAIL = "email"
        const val GUEST = "guest"
    }
}
