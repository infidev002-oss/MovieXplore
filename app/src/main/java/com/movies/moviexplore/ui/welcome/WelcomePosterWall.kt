package com.movies.moviexplore.ui.welcome

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import com.movies.moviexplore.R
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The welcome-back screen's backdrop (used in the kit's ads_welcome_back.xml): a tilted wall of this week's
 * trending movie posters. The same list Home shows, so it normally comes from the HTTP cache. Without a
 * connection the tiles stay as plain poster-shaped cards.
 *
 * Columns follow the width (portrait and landscape), each a stack of 2:3 tiles, every other column shifted
 * so the wall looks staggered.
 */
class WelcomePosterWall @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val tiles = mutableListOf<ImageView>()
    private var posterUrls: List<String> = emptyList()
    private var scope: CoroutineScope? = null
    private var builtFor = 0 to 0

    init {
        orientation = HORIZONTAL
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = MainScope().also { scope ->
            scope.launch {
                posterUrls = runCatching { MediaRepository.shared.trending(MediaType.MOVIE).items }
                    .getOrDefault(emptyList())
                    .mapNotNull { TmdbImage.poster(it.posterPath) }
                bindPosters()
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rebuilt for a new size (e.g. rotation); posted, as children can't be added during layout.
        if (w > 0 && h > 0 && builtFor != w to h) post { build(w, h) }
    }

    private fun build(width: Int, height: Int) {
        builtFor = width to height
        removeAllViews()
        tiles.clear()
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
            repeat(rows) {
                val tile = ImageView(context).apply {
                    setBackgroundResource(R.drawable.welcome_poster_side)
                    clipToOutline = true                   // rounded corners from the card shape
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                stack.addView(tile, LayoutParams(tileWidth, tileHeight).apply { bottomMargin = gap })
                tiles += tile
            }
            addView(stack, LayoutParams(tileWidth, LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        }
        bindPosters()
    }

    private fun bindPosters() {
        if (posterUrls.isEmpty()) return
        // Neighbouring tiles get different posters: walk the list with a stride across the columns.
        tiles.forEachIndexed { index, tile -> ImageLoader.load(tile, posterUrls[(index * 7) % posterUrls.size]) }
    }

    private companion object {
        const val MIN_COLUMNS = 4
        const val COLUMN_DP = 110
        const val GAP_DP = 8
    }
}
