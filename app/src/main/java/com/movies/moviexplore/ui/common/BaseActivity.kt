package com.movies.moviexplore.ui.common

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.movies.moviexplore.mvi.MviStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base for every screen: draws edge to edge (content behind transparent system bars), collects store
 * state and effects on the main thread, and clears its stores when the screen is destroyed.
 *
 * Screens declare `configChanges` in the manifest, so rotation doesn't recreate them and their stores
 * simply carry on.
 */
abstract class BaseActivity : FragmentActivity() {

    protected val uiScope = MainScope()
    private val stores = mutableListOf<MviStore<*, *, *>>()

    private var backHandler: (() -> Unit)? = null
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            backHandler?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    /**
     * While [handler] is set, Back runs it instead of closing the screen (e.g. to close a panel first).
     * Goes through AndroidX's OnBackPressedDispatcher, which covers predictive back on Android 13+ too.
     */
    protected fun interceptBack(handler: (() -> Unit)?) {
        backHandler = handler
        backCallback.isEnabled = handler != null
    }

    /** Registers [this] store so it is cleared with the screen. */
    protected fun <T : MviStore<*, *, *>> T.attach(): T = also { stores += it }

    /** Runs [action] for every value, on the main thread, while the screen exists. */
    protected fun <T> Flow<T>.observe(action: suspend (T) -> Unit) {
        uiScope.launch { collect { action(it) } }
    }

    override fun onDestroy() {
        uiScope.cancel()
        stores.forEach { it.clear() }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun drawEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Keep the navigation bar fully transparent instead of adding a grey scrim.
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Draw under the camera cutout too, so landscape has no black strip on the cutout side.
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (padsSides) keepClearOfSideBars()
    }

    /**
     * True: the whole content is padded clear of the side navigation bar and camera cutout (landscape).
     * A screen with edge-to-edge artwork sets false and pads only its own text and controls ([SystemBars.left] / right).
     */
    protected open val padsSides: Boolean = true

    private fun keepClearOfSideBars() {
        findViewById<View>(android.R.id.content).onSystemBars { bars ->
            val content = findViewById<View>(android.R.id.content)
            if (content.paddingLeft != bars.left || content.paddingRight != bars.right) {
                content.setPadding(bars.left, content.paddingTop, bars.right, content.paddingBottom)
            }
        }
    }
}
