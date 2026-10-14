package com.movies.moviexplore.ui.welcome

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import com.movies.moviexplore.R
import com.movies.moviexplore.ui.common.dp

/**
 * The welcome-back screen's backdrop (used in the kit's ads_welcome_back.xml): a tilted wall of the app's own
 * cartoon poster art (art_poster_*). No real film posters: this screen carries an ad, so it shows no TMDB images.
 *
 * Columns follow the width (portrait and landscape), each a stack of 2:3 tiles, every other column shifted
 * so the wall looks staggered.
 */
class WelcomePosterWall @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var builtFor = 0 to 0

    init {
        orientation = HORIZONTAL
        clipChildren = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rebuilt for a new size (e.g. rotation); posted, as children can't be added during layout.
        if (w > 0 && h > 0 && builtFor != w to h) post { build(w, h) }
    }

    private fun build(width: Int, height: Int) {
        builtFor = width to height
        removeAllViews()
        val gap = dp(GAP_DP)
        val columns = maxOf(MIN_COLUMNS, width / dp(COLUMN_DP))
        val tileWidth = (width - gap * (columns + 1)) / columns
        val tileHeight = tileWidth * 3 / 2
        val rows = height / (tileHeight + gap) + 2      // one extra above and below the tilt
        repeat(columns) { column ->
            val stack = LinearLayout(context).apply {
                orientation = VERTICAL
                // Staggered like a shelf: every other column starts half a poster higher.
                translationY = if (column % 2 == 0) -tileHeight / 2f else 0f
            }
            repeat(rows) { row ->
                val tile = ImageView(context).apply {
                    setBackgroundResource(R.drawable.welcome_poster_side)
                    clipToOutline = true                   // rounded corners from the card shape
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    // Neighbouring tiles get different posters: each column starts two posters further on.
                    setImageResource(POSTERS[(column * 2 + row) % POSTERS.size])
                }
                stack.addView(tile, LayoutParams(tileWidth, tileHeight).apply { bottomMargin = gap })
            }
            addView(stack, LayoutParams(tileWidth, LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        }
    }

    private companion object {
        const val MIN_COLUMNS = 4
        const val COLUMN_DP = 110
        const val GAP_DP = 8
        val POSTERS = intArrayOf(
            R.drawable.art_poster_1, R.drawable.art_poster_2, R.drawable.art_poster_3,
            R.drawable.art_poster_4, R.drawable.art_poster_5, R.drawable.art_poster_6,
        )
    }
}
