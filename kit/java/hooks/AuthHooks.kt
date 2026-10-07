package your.app.hooks

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser

interface AuthHooks {
    // Return true if the hook signed the user in itself; it then calls callback.
    fun beforeSignIn(credential: AuthCredential, callback: (FirebaseUser?, String?) -> Unit): Boolean = false
    fun afterSignIn(user: FirebaseUser, done: () -> Unit) = done()
}
