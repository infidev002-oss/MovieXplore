package com.movies.moviexplore.ads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.movies.moviexplore.R

/** Short "Ad showing…" overlay shown for 1 s before a full-screen ad, to prevent accidental taps. */
class AdLoadingOverlay private constructor(private val dialog: Dialog) {

    fun dismiss() {
        try { if (dialog.isShowing) dialog.dismiss() } catch (_: Exception) { }
    }

    companion object {
        const val SHOW_DELAY_MS = 1000L

        fun show(activity: Activity): AdLoadingOverlay {
            val dialog = Dialog(activity).apply {
                setContentView(R.layout.ads_loading_overlay)
                setCancelable(false)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            if (!activity.isFinishing && !activity.isDestroyed) dialog.show()
            return AdLoadingOverlay(dialog)
        }
    }
}
