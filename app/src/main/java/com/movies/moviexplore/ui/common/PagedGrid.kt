package com.movies.moviexplore.ui.common

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.NativeAdRowAdapter
import com.movies.moviexplore.data.model.Media

/**
 * Drives a `view_media_grid` (its RecyclerView `media_grid`): poster cards, more columns on wider screens,
 * loading the next page near the end, and the loading / error / empty states. Used by Browse, Category,
 * Mood results and Watchlist.
 *
 * With [nativeRow] (the kit's one-row native ad adapter), the ad is the grid's first item, across the full width.
 */
class PagedGrid(
    root: View,
    onOpen: (Media) -> Unit,
    private val onRetry: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    onRemove: ((Media) -> Unit)? = null,
    private val emptyMessage: String = "No results found. Try a different search or filter.",
    private val nativeRow: NativeAdRowAdapter? = null,
) {
    private val grid: RecyclerView = root.findViewById(R.id.media_grid)
    private val loadingMore: View = root.findViewById(R.id.grid_loading_more)
    private val state: View = root.findViewById(R.id.grid_state)
    // Poster cards; with onRemove each card shows a filled bookmark that removes it (the Watchlist).
    private val adapter = ItemAdapter<Media>(R.layout.item_poster, key = { it.key }) { card, media ->
        card.bindPoster(media, onOpen)
        card.findViewById<View>(R.id.poster_bookmark).apply {
            show(onRemove != null)
            setOnClickListener {
                it.tickHaptic()
                onRemove?.invoke(media)
            }
        }
    }
    private val layoutManager = GridLayoutManager(root.context, MIN_COLUMNS)
    private val basePaddingBottom = grid.paddingBottom

    init {
        grid.layoutManager = layoutManager
        grid.adapter = if (nativeRow != null) ConcatAdapter(nativeRow, adapter) else adapter
        grid.addItemDecoration(Spacing(grid.dimen(R.dimen.grid_gap_horizontal), grid.dimen(R.dimen.grid_gap_vertical)))
        applySpanCount(MIN_COLUMNS)
        // As many columns as fit at MIN_COLUMN_DP: 3 on phones in portrait, more in landscape and on tablets.
        // Screens handle rotation themselves, so this re-runs whenever the grid's width changes.
        grid.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) fitColumns(right - left)
        }
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            // Scrolling the results hides the keyboard, so search results aren't half covered.
            override fun onScrollStateChanged(view: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) grid.hideKeyboard()
            }

            // Also called after each layout, so a first page shorter than the screen still asks for more.
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                val total = layoutManager.itemCount
                if (total > 0 && layoutManager.findLastVisibleItemPosition() >= total - NEAR_END) onLoadMore?.invoke()
            }
        })
    }

    /** [emptyText] replaces the default empty message for this render (e.g. when filters match nothing). */
    fun render(items: List<Media>, isLoading: Boolean, isLoadingMore: Boolean = false, error: String? = null, emptyText: String? = null) {
        adapter.submit(items)
        grid.show(items.isNotEmpty())
        loadingMore.show(isLoadingMore)
        val message = when {
            items.isNotEmpty() || isLoading -> null
            error != null -> error
            else -> emptyText ?: emptyMessage
        }
        state.renderState(loading = isLoading && items.isEmpty(), message = message, onRetry = onRetry.takeIf { error != null })
    }

    private fun fitColumns(width: Int) {
        val spacing = grid.dimen(R.dimen.grid_gap_horizontal)
        val available = width - grid.paddingLeft - grid.paddingRight
        val columns = maxOf(MIN_COLUMNS, (available + spacing) / (grid.dp(MIN_COLUMN_DP) + spacing))
        // Posted: changing the column count asks for a new layout, which can't happen inside this one.
        if (layoutManager.spanCount != columns) grid.post { applySpanCount(columns) }
    }

    private fun applySpanCount(columns: Int) {
        layoutManager.spanCount = columns
        nativeRow?.let { layoutManager.spanSizeLookup = it.fullSpanLookup(columns) }   // ad row = full width
        grid.invalidateItemDecorations()
    }

    fun scrollToTop() = grid.scrollToPosition(0)

    /** Extra room at the bottom (e.g. for the floating tab bar), in px. */
    fun setBottomSpace(px: Int) {
        grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, basePaddingBottom + px)
        (loadingMore.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = px
    }

    /** Gaps between poster columns and rows. The native ad row keeps its own built-in spacing. */
    private inner class Spacing(private val horizontal: Int, private val vertical: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val adapterPosition = parent.getChildAdapterPosition(view).takeIf { it != RecyclerView.NO_POSITION } ?: return
            val position = adapterPosition - (nativeRow?.itemCount ?: 0)   // poster index
            if (position < 0) return
            val columns = layoutManager.spanCount
            val column = position % columns
            outRect.left = column * horizontal / columns
            outRect.right = horizontal - (column + 1) * horizontal / columns
            outRect.top = if (position >= columns) vertical else 0
        }
    }

    private companion object {
        const val MIN_COLUMNS = 3
        const val MIN_COLUMN_DP = 110
        const val NEAR_END = 6
    }
}
