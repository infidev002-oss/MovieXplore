package your.app.playstore

import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import your.app.ads.WelcomeBack

/**
 * Google Play in-app update, IMMEDIATE (full-screen, Play handles download + install).
 * Create it as a property of the home Activity, so its result launcher is registered before onStart:
 *
 *   private val inAppUpdate = InAppUpdate(this)
 *   override fun onCreate(savedInstanceState: Bundle?) {
 *       ...
 *       if (savedInstanceState == null) inAppUpdate.check { AppReview.maybeRequest(this) }
 *   }
 *
 * check() runs once per app process (rotation / returning to the screen won't re-check).
 * [onNoUpdate] runs when there is no update, the check fails, or the user cancels the update.
 * An update interrupted by leaving the app is resumed in onResume, as Google requires.
 * Only works for apps installed from Google Play (otherwise onNoUpdate runs).
 */
class InAppUpdate(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    private var manager: AppUpdateManager? = null
    private var onNoUpdate: (() -> Unit)? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) runNoUpdate()   // cancelled / failed
    }

    init { activity.lifecycle.addObserver(this) }

    private fun manager(): AppUpdateManager? = manager ?: try {
        AppUpdateManagerFactory.create(activity.applicationContext).also { manager = it }
    } catch (_: Exception) { null }

    fun check(onNoUpdate: () -> Unit = {}) {
        this.onNoUpdate = onNoUpdate
        if (checked) { runNoUpdate(); return }
        checked = true
        val m = manager() ?: return runNoUpdate()
        try {
            m.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (!isAlive()) return@addOnSuccessListener
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        if (!start(m, info)) runNoUpdate()
                    } else runNoUpdate()
                }
                .addOnFailureListener { runNoUpdate() }
        } catch (_: Exception) { runNoUpdate() }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!checked) return
        val m = manager() ?: return
        try {
            m.appUpdateInfo.addOnSuccessListener { info ->
                if (isAlive() && info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    start(m, info)
                }
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        onNoUpdate = null
        owner.lifecycle.removeObserver(this)
    }

    private fun start(m: AppUpdateManager, info: AppUpdateInfo): Boolean = try {
        WelcomeBack.skipNextResume()   // the Play update screen takes the app to the background
        m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
    } catch (_: Exception) { false }

    private fun runNoUpdate() {
        val callback = onNoUpdate ?: return
        onNoUpdate = null
        if (isAlive()) try { callback() } catch (_: Exception) { }
    }

    private fun isAlive(): Boolean =
        !activity.isFinishing && !activity.isDestroyed && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)

    private companion object {
        @Volatile var checked = false   // once per process
    }
}
