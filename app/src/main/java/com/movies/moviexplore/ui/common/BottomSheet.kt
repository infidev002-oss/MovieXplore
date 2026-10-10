package com.movies.moviexplore.ui.common

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.movies.moviexplore.R

/**
 * Shows [layout] as a bottom sheet: it slides up over a dimmed screen, and tapping outside it or
 * pressing Back closes it (calling [onDismiss]). [bind] fills the sheet's views.
 */
fun Activity.showBottomSheet(layout: Int, onDismiss: () -> Unit, bind: (dialog: Dialog, sheet: View) -> Unit): Dialog {
    val dialog = Dialog(this, R.style.BottomSheetDialog)
    val root = LayoutInflater.from(this).inflate(R.layout.dialog_bottom_sheet, null) as FrameLayout
    val sheet = LayoutInflater.from(this).inflate(layout, root, false)
    root.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    root.setOnClickListener { dialog.cancel() }
    // Taps on the sheet itself must not fall through to the root and close it.
    sheet.isClickable = true

    dialog.setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    dialog.window?.apply {
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Android 15+ always draws dialogs edge to edge; on 11-14 it has to be requested.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.R until Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            setDecorFitsSystemWindows(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Same as the screens: no grey/white scrim behind the navigation bar.
            isNavigationBarContrastEnforced = false
        }
    }
    // The sheet reaches the bottom edge; keep its content above the navigation bar.
    sheet.padForSystemBars(bottom = true)
    dialog.setOnDismissListener { onDismiss() }
    bind(dialog, sheet)
    dialog.show()
    return dialog
}

/**
 * Like [showBottomSheet], but for a long list: the sheet opens at [startFraction] of the screen height, and
 * scrolling its list up (or dragging the sheet up) grows it to full screen. Dragging down shrinks it again,
 * then closes it. [layout] fills the full height; its list takes the space left (height 0dp, weight 1).
 */
fun Activity.showExpandableSheet(
    layout: Int,
    onDismiss: () -> Unit,
    startFraction: Float = 0.6f,
    bind: (dialog: Dialog, sheet: View) -> Unit,
): Dialog {
    val dialog = BottomSheetDialog(this, R.style.ExpandableSheetDialog)
    val sheet = LayoutInflater.from(this).inflate(layout, null)
    dialog.setContentView(sheet, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    dialog.behavior.apply {
        peekHeight = (resources.displayMetrics.heightPixels * startFraction).toInt()
        state = BottomSheetBehavior.STATE_COLLAPSED
    }
    sheet.padForSystemBars(bottom = true)
    dialog.setOnDismissListener { onDismiss() }
    bind(dialog, sheet)
    dialog.show()
    return dialog
}
