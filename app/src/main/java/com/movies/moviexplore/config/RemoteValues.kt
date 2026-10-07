package com.movies.moviexplore.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.movies.moviexplore.hooks.AdRules
import org.json.JSONObject

object RemoteValues {
    const val BANNER_HOME = "gle_banner_id1"
    const val BANNER_LIST = "gle_banner_id2"
    const val BANNER_DETAILS = "gle_banner_id3"
    // The four native placements (one Remote Config unit each).
    const val NATIVE_OB = "gle_native_ob"               // onboarding screen
    const val NATIVE_HOME = "gle_native_home"           // home
    const val NATIVE_SETTINGS = "gle_native_settings"   // settings
    const val NATIVE_CATEGORY = "gle_native_category"   // category list (a selected genre's list screen)
    const val INTER = "gle_inter_id"
    const val SPLASH_INTER = "gle_splashInter_id"
    const val WELCOME_INTER = "gle_welcome_inter_id"   // WelcomeBack screen interstitial

    @Volatile private var cache: JSONObject? = null

    /** Call after fetchAndActivate() completes. */
    fun reload() { cache = null }

    private fun settings(): JSONObject {
        cache?.let { return it }
        return try {
            JSONObject(FirebaseRemoteConfig.getInstance().getString("Data"))
                .getJSONObject("APP_SETTINGS").also { cache = it }
        } catch (e: Exception) { JSONObject() }
    }

    val adStatus: Boolean get() = settings().optBoolean("ad_status", true)
    // Remote Config stores all intervals in SECONDS; these getters return MILLISECONDS.
    val interAdInterval: Long get() = settings().optLong("InterAdInterval", 45L) * 1000
    val nativeAdInterval: Long get() = settings().optLong("NativeAdInterval", 20L) * 1000   // per native placement
    val urlAdInterval: Long get() = settings().optLong("URLAdInterval", 20L) * 1000
    val splashCounter: Long get() = settings().optLong("SplashCounter", 8L)       // seconds (max wait for splash ad)
    val privacyPolicyUrl: String get() = settings().optString("ppLink", "")
    val termsUrl: String get() = settings().optString("tncLink", "")
    val minSupportedVersion: Long get() = settings().optLong("min_supported_version", 0L)

    fun adUnitIds(key: String): List<String> {
        val arr = settings().optJSONObject(key)?.optJSONArray("id") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it, "").trim() }.filter { it.isNotEmpty() }
    }

    /** Native CTA button height in dp from the unit's "heightCTA" (number; missing / "default" = 40). */
    fun nativeCtaHeight(key: String): Int {
        val raw = settings().optJSONObject(key)?.opt("heightCTA") ?: return 40
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: 40
            else -> 40
        }
    }

    /** Optional native layout for a unit: "small" / "medium" / "large" (null = the size the code asks for). */
    fun nativeLayout(key: String): String? =
        settings().optJSONObject(key)?.optString("layout", "")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isAdUnitEnabled(key: String): Boolean {
        val enabled = settings().optJSONObject(key)?.optBoolean("isEnabled", false) ?: false
        return adStatus && AdRules.isAdUnitEnabled(key, enabled)
    }

    fun updateDialog(): JSONObject? = try {
        JSONObject(FirebaseRemoteConfig.getInstance().getString("Update_App"))  // appTitle, appDesc, appIcon, appURL
    } catch (e: Exception) { null }
}
