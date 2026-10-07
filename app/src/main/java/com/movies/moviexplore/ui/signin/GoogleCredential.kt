package com.movies.moviexplore.ui.signin

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.movies.moviexplore.R
import com.movies.moviexplore.ui.common.toast

/**
 * A Google credential from Credential Manager for AuthManager (AGENTS.md §9). Null when the user closed
 * the picker or it failed; a failure also shows a message.
 */
suspend fun getGoogleCredential(activity: Activity): AuthCredential? {
    val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
        } else null
    } catch (e: GetCredentialCancellationException) { null }   // user closed the picker
    catch (e: GetCredentialException) {
        activity.toast("Google sign-in isn't available right now. Please try again.", long = true)
        null
    }
}

/** After sign-out or account deletion, so the next sign-in shows the account picker again. */
suspend fun clearGoogleCredential(context: Context) {
    runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
}
