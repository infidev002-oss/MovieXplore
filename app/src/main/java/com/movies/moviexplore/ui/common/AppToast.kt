package com.movies.moviexplore.ui.common

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.movies.moviexplore.R

/**
 * The app's own toast: a glass pill with an icon badge that slides up above the navigation bar,
 * replacing any one still showing. Use instead of the system [toast] for in-app confirmations.
 * It sits above [above] when given (e.g. a drawer's action row it must not cover), else above the
 * floating tab bar or the bottom banner ad on screens that show one.
 */
fun Activity.showAppToast(message: String, icon: Int = R.drawable.ic_check, above: View? = null) {
    val content = findViewById<FrameLayout>(android.R.id.content)
    content.findViewWithTag<View>(AppToast.TAG)?.let { old ->
        old.animate().cancel()
        content.removeView(old)
    }

    val toast = content.inflate(R.layout.view_app_toast)
    toast.tag = AppToast.TAG
    toast.findViewById<ImageView>(R.id.toast_icon).setImageResource(icon)
    toast.findViewById<TextView>(R.id.toast_message).text = message
    content.addView(
        toast,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            marginStart = dp(24)
            marginEnd = dp(24)
            val anchor = listOfNotNull(above, findViewById(R.id.nav_bar), findViewById(R.id.banner_root)).firstOrNull { it.isShown }
            bottomMargin = if (anchor != null) content.bottomInWindow() - anchor.topInWindow() + dp(12) else navigationBarHeight() + dp(28)
        },
    )
    toast.announceForAccessibility(message)

    toast.alpha = 0f
    toast.translationY = dp(24).toFloat()
    toast.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).withEndAction {
        toast.animate().alpha(0f).translationY(dp(12).toFloat()).setStartDelay(AppToast.SHOW_MS).setDuration(180)
            .withEndAction { content.removeView(toast) }.start()
    }.start()
}

/** [Activity.showAppToast] from a view, for classes that only hold views (e.g. drawers, panels). */
fun View.showAppToast(message: String, icon: Int = R.drawable.ic_check, above: View? = null) {
    var ctx = context
    while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
    (ctx as? Activity)?.showAppToast(message, icon, above)
}

private fun View.topInWindow(): Int = IntArray(2).also { getLocationInWindow(it) }[1]

private fun View.bottomInWindow(): Int = topInWindow() + height

private object AppToast {
    const val TAG = "app_toast"
    const val SHOW_MS = 2000L
}

@Suppress("DEPRECATION")
private fun Activity.navigationBarHeight(): Int {
    val insets = window.decorView.rootWindowInsets ?: return 0
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insets.getInsets(WindowInsets.Type.navigationBars()).bottom
    } else {
        insets.systemWindowInsetBottom
    }
}
