package your.app.playstore

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import your.app.ads.WelcomeBack

/** Small Play Store helpers shared by ForceUpdate, InAppUpdate and AppReview. */
object PlayStore {

    fun versionCode(context: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    } catch (_: Exception) { Long.MAX_VALUE }   // unknown: never force an update

    /** Opens [url] if given, otherwise this app's Play Store page (Play app first, then the browser). */
    fun open(context: Context, url: String? = null): Boolean {
        val targets = buildList {
            if (!url.isNullOrBlank()) add(url)
            add("market://details?id=${context.packageName}")
            add("https://play.google.com/store/apps/details?id=${context.packageName}")
        }
        for (target in targets) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, target.toUri())
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                WelcomeBack.skipNextResume()
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: Exception) { }
        }
        return false
    }
}
