package com.movies.moviexplore.splash

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.movies.moviexplore.ads.AdNavigator
import com.movies.moviexplore.ads.AdsConsent
import com.movies.moviexplore.ads.BannerAd
import com.movies.moviexplore.ads.SplashInterstitial
import com.movies.moviexplore.auth.AuthManager
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.hooks.Startup
import com.movies.moviexplore.playstore.ForceUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * The whole splash sequence. Your SplashActivity only designs the screen:
 *
 *   class SplashActivity : AppCompatActivity() {
 *       private val flow = SplashFlow(this)            // property: registers the permission launcher
 *       override fun onCreate(savedInstanceState: Bundle?) {
 *           super.onCreate(savedInstanceState)
 *           setContentView(…)
 *           flow.start(
 *               onStatus = { status -> /* OFFLINE: "turn on internet" · LOADING: "please wait" */ },
 *               bannerContainer = binding.splashBanner,      // optional splash banner slot
 *               onDone = { destination -> startActivity(destination ?: Intent(this, HomeActivity::class.java)); finish() }
 *           )
 *       }
 *   }
 *
 * Order (fixed):
 *  1. Wait for internet.
 *  2. Remote Config fetch (resets first if its saved fetch time is in the future), then the Startup hook.
 *     In parallel: consent (+ Mobile Ads init) and the notification permission (Android 13+).
 *  3. Force update (blocking dialog, stops here).
 *  4. AuthManager.recordAppOpen(), interstitial preload, splash interstitial (waits up to SplashCounter s).
 *  5. onDone(destination): a non-null destination from the Startup hook always wins.
 *
 * Safety: the Startup hook runs once per launch even if the splash is recreated (a recreated splash
 * reuses the running one); it can't crash the splash, and after STARTUP_MAX_MS the splash continues anyway.
 * Back is blocked while the splash runs.
 */
class SplashFlow(private val activity: AppCompatActivity) {

    enum class Status { OFFLINE, LOADING }

    private val permissionResult = CompletableDeferred<Unit>()
    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionResult.complete(Unit) }
    private val splashAd = SplashInterstitial()
    private var started = false

    init {
        activity.onBackPressedDispatcher.addCallback(activity, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}          // no back during the splash
        })
    }

    /**
     * @param bannerContainer optional splash banner slot (an empty FrameLayout, visibility="gone"). The kit loads
     *        RemoteValues.BANNER_SPLASH into it once, right after consent; no reload on the splash.
     */
    fun start(
        onStatus: (Status) -> Unit = {},
        bannerContainer: FrameLayout? = null,
        onDone: (destination: Intent?) -> Unit
    ) {
        if (started) return
        started = true
        activity.lifecycleScope.launch {
            if (!isOnline(activity)) {
                onStatus(Status.OFFLINE)
                awaitNetwork(activity)
            }
            onStatus(Status.LOADING)
            log("online")

            val prepared = prepare(activity)                 // Remote Config, then Startup hook (shared)
            val consent = async { gatherConsent() }
            if (bannerContainer != null) launch {
                consent.await()                                // Mobile Ads is initialised after consent
                if (!activity.isFinishing && !activity.isDestroyed) {
                    BannerAd.load(activity, activity, bannerContainer, RemoteValues.BANNER_SPLASH)
                }
            }
            val permission = async { requestNotificationPermission() }
            val destination = prepared.await()
            log("startup done, destination=${destination?.component?.shortClassName}")
            consent.await(); permission.await()
            log("consent + permission done")

            if (ForceUpdate.isRequired(activity)) {
                log("force update required")
                inFlight = null                              // the app closes from the dialog; next launch starts fresh
                ForceUpdate.show(activity)
                return@launch
            }

            AuthManager.recordAppOpen()
            AdNavigator.preload(activity)
            splashAd.loadAndWait(activity)
            splashAd.showThen(activity) {
                if (activity.isFinishing || activity.isDestroyed) return@showThen
                inFlight = null                              // next launch starts fresh
                log("done")
                onDone(destination)
            }
        }
    }

    private suspend fun gatherConsent() = suspendCancellableCoroutine { cont ->
        AdsConsent.gather(activity) { if (cont.isActive) cont.resume(Unit) }
    }

    private suspend fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return
        try { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (_: Exception) { return }
        permissionResult.await()
    }

    companion object {
        private const val TAG = "SplashFlow"
        private val t0 = SystemClock.elapsedRealtime()
        private fun log(step: String) = Log.d(TAG, "+${SystemClock.elapsedRealtime() - t0} ms $step")

        private const val RC_FETCH_TIMEOUT_MS = 10_000L
        private const val RC_RESET_TIMEOUT_MS = 5_000L
        /** Safety net for the Startup hook (its own timeout should be shorter). */
        const val STARTUP_MAX_MS = 30_000L

        // Survives splash recreation: work started by the first splash instance is reused, not repeated.
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        @Volatile private var inFlight: Deferred<Intent?>? = null

        // Running or finished-but-not-yet-used (the splash was recreated) = reuse; cleared once used.
        private fun prepare(activity: AppCompatActivity): Deferred<Intent?> =
            inFlight?.also { log("reusing running startup") } ?: processScope.async {
                fetchRemoteConfig()
                log("remote config ready, starting Startup hook")
                runStartupHook(activity)
            }.also { inFlight = it }

        private suspend fun fetchRemoteConfig() {
            val rc = FirebaseRemoteConfig.getInstance()
            try {
                rc.setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0).build()
                ).await()
            } catch (_: Exception) { }
            // Clock was set ahead, then corrected: the saved fetch time is in the future and Remote Config
            // would keep serving its cache until then. Reset so this fetch reaches the server.
            if (rc.info.fetchTimeMillis > System.currentTimeMillis() + 60_000) {
                try { withTimeoutOrNull(RC_RESET_TIMEOUT_MS.milliseconds) { rc.reset().await() } } catch (_: Exception) { }
            }
            try { withTimeoutOrNull(RC_FETCH_TIMEOUT_MS.milliseconds) { rc.fetchAndActivate().await() } } catch (_: Exception) { }
            RemoteValues.reload()                             // failed / throttled fetch = cached values
        }

        // Wrapped so "no destination" (null) and "timed out" stay distinguishable.
        private class HookResult(val destination: Intent?)

        private suspend fun runStartupHook(activity: AppCompatActivity): Intent? {
            val result = withTimeoutOrNull(STARTUP_MAX_MS.milliseconds) {
                suspendCancellableCoroutine { cont ->
                    try {
                        Startup.run(activity) { destination ->
                            processScope.launch { if (cont.isActive) cont.resume(HookResult(destination)) }
                        }
                    } catch (e: Exception) {
                        log("Startup hook threw: ${e.javaClass.simpleName}")
                        if (cont.isActive) cont.resume(HookResult(null))
                    }
                }
            }
            if (result == null) log("Startup hook timed out after $STARTUP_MAX_MS ms")
            return result?.destination
        }

        private fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private suspend fun awaitNetwork(context: Context) = suspendCancellableCoroutine { cont ->
            val cm = context.getSystemService(ConnectivityManager::class.java)
            if (cm == null) { cont.resume(Unit); return@suspendCancellableCoroutine }
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    try { cm.unregisterNetworkCallback(this) } catch (_: Exception) { }
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            try { cm.registerDefaultNetworkCallback(callback) } catch (_: Exception) { cont.resume(Unit); return@suspendCancellableCoroutine }
            cont.invokeOnCancellation { try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) { } }
        }
    }
}
