package com.movies.moviexplore.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.movies.moviexplore.R

/**
 * A frosted-glass panel: on Android 12+ it shows a live blur of whatever [enableBlur]'s source draws
 * behind it, under a light tint. Older versions keep the panel's solid background from XML.
 *
 * Plain Android has no "blur what's behind this view", so a hidden layer copies the area behind the
 * panel into a small bitmap whenever the screen redraws (a quarter size, so it's cheap) and blurs it on
 * the GPU with RenderEffect. The blur is redrawn only when that copy changed, so a still screen stays idle.
 * While copying, glass panels skip drawing themselves, so a panel never captures itself.
 */
class GlassFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        // Clips the blur layer and children to the rounded background.
        clipToOutline = true
    }

    /**
     * Blurs [source] behind this panel and switches to [tintedBackground], a lighter, see-through
     * version of the panel background, with [border] drawn on top of the blur (the blur layer would
     * otherwise cover the background's edge). Does nothing below Android 12.
     */
    fun enableBlur(source: View, tintedBackground: Int, border: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (getChildAt(0) is BackdropBlurView) return
        addView(BackdropBlurView(context, this, source), 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setBackgroundResource(tintedBackground)
        foreground = context.getDrawable(border)
    }

    override fun draw(canvas: Canvas) {
        if (capturing) return
        super.draw(canvas)
    }

    // The panel takes its size from its real content; the blur layer measures as 0 so it never makes a
    // wrap_content panel (like the tab bar) grow, and is then laid out over the panel's final bounds.
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        (getChildAt(0) as? BackdropBlurView)?.layout(0, 0, width, height)
    }

    private companion object {
        /** True while a backdrop is being copied; glass panels don't draw then. */
        var capturing = false
        const val DOWNSCALE = 4
        const val BLUR_RADIUS_DP = 22
    }

    private class BackdropBlurView(context: Context, private val panel: View, private val source: View) : View(context) {

        private var bitmap: Bitmap? = null
        private var scratch: Bitmap? = null   // the next copy; swapped in only when it differs from [bitmap]
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val destination = Rect()
        private val panelLocation = IntArray(2)
        private val sourceLocation = IntArray(2)
        private val backgroundColor = context.getColor(R.color.background)

        private val onPreDraw = ViewTreeObserver.OnPreDrawListener {
            if (panel.isShown && width > 0 && height > 0) capture()
            true
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = BLUR_RADIUS_DP * resources.displayMetrics.density
                setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(0, 0)

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            viewTreeObserver.addOnPreDrawListener(onPreDraw)
        }

        override fun onDetachedFromWindow() {
            viewTreeObserver.removeOnPreDrawListener(onPreDraw)
            bitmap?.recycle()
            bitmap = null
            scratch?.recycle()
            scratch = null
            super.onDetachedFromWindow()
        }

        /**
         * Copies the part of [source] behind this view into a small bitmap, and redraws the blur only when
         * that copy changed. Redrawing every time would ask for a new frame every frame, so the screen would
         * never rest and scrolling would stutter.
         */
        private fun capture() {
            val w = (width / DOWNSCALE).coerceAtLeast(1)
            val h = (height / DOWNSCALE).coerceAtLeast(1)
            val target = scratch?.takeIf { it.width == w && it.height == h }
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { scratch?.recycle(); scratch = it }

            getLocationInWindow(panelLocation)
            source.getLocationInWindow(sourceLocation)
            target.eraseColor(backgroundColor)
            val canvas = Canvas(target)
            canvas.scale(1f / DOWNSCALE, 1f / DOWNSCALE)
            canvas.translate((sourceLocation[0] - panelLocation[0]).toFloat(), (sourceLocation[1] - panelLocation[1]).toFloat())
            capturing = true
            try {
                source.draw(canvas)
            } finally {
                capturing = false
            }
            if (bitmap?.sameAs(target) == true) return   // nothing behind the panel changed
            scratch = bitmap
            bitmap = target
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val frame = bitmap ?: return
            destination.set(0, 0, width, height)
            canvas.drawBitmap(frame, null, destination, paint)
        }
    }
}
