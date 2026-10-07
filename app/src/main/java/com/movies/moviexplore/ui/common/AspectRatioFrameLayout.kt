package com.movies.moviexplore.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.movies.moviexplore.R

/** A FrameLayout whose height follows its width: posters are 2:3, video thumbnails 16:9. */
class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** Width divided by height. */
    var aspectRatio: Float = 1f
        set(value) {
            field = value
            requestLayout()
        }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.AspectRatioFrameLayout).apply {
            aspectRatio = getFloat(R.styleable.AspectRatioFrameLayout_aspectRatio, 1f)
            recycle()
        }
        // Clip children (the image) to the rounded background.
        clipToOutline = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / aspectRatio).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
