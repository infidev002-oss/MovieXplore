package your.app.hooks

import android.app.Activity
import your.app.config.RemoteValues

object AdRules {
    // Keep these defaults exactly as they are.
    @Volatile var interAdInterval: () -> Long = { RemoteValues.interAdInterval }
    @Volatile var nativeAdInterval: () -> Long = { RemoteValues.nativeAdInterval }
    @Volatile var isAdUnitEnabled: (unitKey: String, isEnabled: Boolean) -> Boolean = { _, enabled -> enabled }
    // Called before an interstitial on navigation. true = already handled, navigate without the interstitial.
    @Volatile var beforeInterstitial: (activity: Activity) -> Boolean = { false }
    // Largest native CTA button height in dp (heightCTA from Remote Config is clamped to 36..this).
    @Volatile var nativeCtaMaxDp: () -> Int = { 46 }
}
