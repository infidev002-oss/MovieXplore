package com.movies.moviexplore.ui.common

import android.animation.AnimatorInflater
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import com.movies.moviexplore.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun View.dp(value: Int): Int = context.dp(value)

/** A size from res/values/dimens.xml (sdp based, so it scales with the screen), in pixels. */
fun Context.dimen(id: Int): Int = resources.getDimensionPixelSize(id)

fun View.dimen(id: Int): Int = context.dimen(id)

fun Context.toast(message: String, long: Boolean = false) =
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

/** True when the active network offers internet (the same check as the splash). */
fun Context.isOnline(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun ViewGroup.inflate(layout: Int, attach: Boolean = false): View =
    LayoutInflater.from(context).inflate(layout, this, attach)

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun View.hideKeyboard() {
    context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    clearFocus()
}

/**
 * Keeps a button's drawableStart beside its centered text instead of pinned to the button's edge,
 * by padding both sides equally so icon + gap + text sit in the middle.
 */
fun TextView.centerStartIcon() {
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val icon = compoundDrawablesRelative[0] ?: return@addOnLayoutChangeListener
        val content = icon.bounds.width() + compoundDrawablePadding + paint.measureText(text.toString()).toInt()
        val side = ((width - content) / 2).coerceAtLeast(0)
        if (paddingStart != side || paddingEnd != side) post { setPaddingRelative(side, paddingTop, side, paddingBottom) }
    }
}

/** A light tick, for toggles such as the bookmark. */
fun View.tickHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

/**
 * Heights of the status bar (top) and navigation bar (bottom), in px. [left] / [right] are the side
 * navigation bar and camera cutout in landscape (0 in portrait).
 */
data class SystemBars(val top: Int, val bottom: Int, val left: Int = 0, val right: Int = 0)

/**
 * Calls [block] with the system bar sizes whenever they change. The app draws behind transparent
 * system bars, so views that must not sit under them pad themselves with these values.
 */
@Suppress("DEPRECATION")
fun View.onSystemBars(block: (SystemBars) -> Unit) {
    setOnApplyWindowInsetsListener { _, insets ->
        val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sides = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            insets.getInsets(WindowInsets.Type.systemBars()).let { SystemBars(it.top, it.bottom, sides.left, sides.right) }
        } else {
            // DisplayCutout exists from Android 9 (API 28); Android 8.x has no cutout insets.
            var cutoutLeft = 0
            var cutoutRight = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout?.let { cutoutLeft = it.safeInsetLeft; cutoutRight = it.safeInsetRight }
            }
            SystemBars(
                insets.systemWindowInsetTop,
                insets.systemWindowInsetBottom,
                maxOf(insets.systemWindowInsetLeft, cutoutLeft),
                maxOf(insets.systemWindowInsetRight, cutoutRight),
            )
        }
        block(bars)
        insets
    }
    if (isAttachedToWindow) requestApplyInsets()
}

/** Adds the status bar height to the top padding and/or the navigation bar height to the bottom padding. */
fun View.padForSystemBars(top: Boolean = false, bottom: Boolean = false) {
    val start = paddingTop
    val end = paddingBottom
    onSystemBars { bars ->
        setPadding(paddingLeft, start + if (top) bars.top else 0, paddingRight, end + if (bottom) bars.bottom else 0)
    }
}

/** Space to keep clear at the bottom of tab screens: the system navigation bar, the floating tab bar and a gap. */
fun Context.bottomBarSpace(navigationBar: Int): Int = navigationBar + dimen(R.dimen.floating_nav_bar_space)


/**
 * A rounded pill toggle: white with dark text when selected, dark with light text otherwise.
 * Pressing it gives the app's shrink feedback (pills are created in several layouts and in code, so it's set here).
 */
fun TextView.setPillSelected(selected: Boolean) {
    if (stateListAnimator == null) stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.press_scale)
    setBackgroundResource(if (selected) R.drawable.bg_pill_selected else R.drawable.bg_pill)
    setTextColor(context.getColor(if (selected) R.color.background else R.color.text_primary))
    isSelected = selected
}
