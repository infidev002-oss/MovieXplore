package your.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import your.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UMP consent + Mobile Ads initialization.
 *
 * The consent form appears only for users in regions that need it (EEA, UK, some US states) and
 * only when a GDPR / US-states message is published for the app in AdMob → Privacy & messaging.
 * To test from anywhere: put the device's hashed id (printed in Logcat by the UMP SDK) in
 * local.properties as UMP_TEST_DEVICE_ID; debug builds then act as if the device is in the EEA.
 */
object AdsConsent {
    private const val TAG = "AdsConsent"
    private val adsInitialized = AtomicBoolean(false)

    private fun info(context: Context): ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    fun canRequestAds(context: Context): Boolean = info(context).canRequestAds()

    fun isPrivacyOptionsRequired(context: Context): Boolean =
        info(context).privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Not shown in the app by decision. Kept so a "Privacy options" entry can be added later if needed. */
    fun showPrivacyOptions(activity: Activity, done: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
            done()
        }
    }

    /** Call once per app launch from the splash. Always calls done (on the main thread). */
    fun gather(activity: Activity, done: () -> Unit) {
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) {
            params.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_ID)
                    .build()
            )
        }
        info(activity).requestConsentInfoUpdate(activity, params.build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                initAds(activity, done)
            }
        }, { error ->
            Log.w(TAG, "Consent info update failed: ${error.message}")
            initAds(activity, done)
        })
    }

    private fun initAds(activity: Activity, done: () -> Unit) {
        if (!canRequestAds(activity) || !adsInitialized.compareAndSet(false, true)) {
            activity.runOnUiThread(done); return
        }
        if (BuildConfig.DEBUG && BuildConfig.ADS_TEST_DEVICE_ID.isNotBlank()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(listOf(BuildConfig.ADS_TEST_DEVICE_ID)).build()
            )
        }
        MobileAds.initialize(activity.applicationContext) { activity.runOnUiThread(done) }
    }
}
