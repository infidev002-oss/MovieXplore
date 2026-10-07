package your.app.auth

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import your.app.hooks.AuthHooks
import your.app.watchlist.WatchlistRepository

object AuthManager {
    // Keep this default exactly as is.
    @Volatile var hooks: AuthHooks = object : AuthHooks {}

    private val auth get() = FirebaseAuth.getInstance()
    private fun userRef(uid: String) = FirebaseDatabase.getInstance().getReference("Users").child(uid)

    fun currentUser(): FirebaseUser? = auth.currentUser?.takeIf { !it.isAnonymous }
    fun isSignedIn(): Boolean = currentUser() != null

    /** The sign-in screen gets a Google ID token (Credential Manager) and calls this with
     *  GoogleAuthProvider.getCredential(idToken, null). */
    fun signInWithGoogle(credential: AuthCredential, callback: (FirebaseUser?, String?) -> Unit) {
        val wrapped: (FirebaseUser?, String?) -> Unit = { user, error ->
            if (user != null) onSignInSuccess(user, callback) else callback(null, error)
        }
        if (hooks.beforeSignIn(credential, wrapped)) return
        auth.signInWithCredential(credential)
            .addOnSuccessListener { wrapped(it.user, null) }
            .addOnFailureListener { wrapped(null, it.message) }
    }

    private fun onSignInSuccess(user: FirebaseUser, callback: (FirebaseUser?, String?) -> Unit) {
        val profile = userRef(user.uid).child("Profile")
        profile.child("createdAt").get().addOnCompleteListener { existing ->
            val data = hashMapOf<String, Any>(
                "email" to (user.email ?: ""),
                "name" to (user.displayName ?: ""),
                "lastAppOpenAt" to ServerValue.TIMESTAMP
            )
            if (!(existing.isSuccessful && existing.result.exists())) data["createdAt"] = ServerValue.TIMESTAMP
            profile.updateChildren(data).addOnCompleteListener {
                hooks.afterSignIn(user) {
                    WatchlistRepository.onSignedIn(user.uid)
                    callback(user, null)
                }
            }
        }
    }

    /** Call once per app open (splash). Updates Profile/lastAppOpenAt for a signed-in user. */
    fun recordAppOpen() {
        val user = currentUser() ?: return
        userRef(user.uid).child("Profile").updateChildren(mapOf<String, Any>("lastAppOpenAt" to ServerValue.TIMESTAMP))
    }

    /** UI also clears Credential Manager state (clearCredentialState) after this. */
    fun signOut() {
        WatchlistRepository.onSignedOut()
        auth.signOut()
    }

    /** callback(null) = deleted. callback("reauth_required") = UI gets a fresh Google
     *  credential and calls reauthenticateAndDelete(). Any other string = error message. */
    fun deleteAccount(callback: (String?) -> Unit) {
        val user = currentUser() ?: return callback("not_signed_in")
        userRef(user.uid).removeValue().addOnCompleteListener {
            user.delete()
                .addOnSuccessListener { WatchlistRepository.onSignedOut(); callback(null) }
                .addOnFailureListener { e ->
                    callback(if (e is FirebaseAuthRecentLoginRequiredException) "reauth_required" else e.message)
                }
        }
    }

    fun reauthenticateAndDelete(credential: AuthCredential, callback: (String?) -> Unit) {
        val user = currentUser() ?: return callback("not_signed_in")
        user.reauthenticate(credential)
            .addOnSuccessListener { deleteAccount(callback) }
            .addOnFailureListener { callback(it.message) }
    }
}
