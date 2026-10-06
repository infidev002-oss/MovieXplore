package com.movies.myapplication.ui.common

import android.view.View
import android.widget.GridView
import com.movies.myapplication.R
import com.movies.myapplication.data.model.Media

/**
 * Drives a `view_media_grid`: poster cards, loading the next page near the end, and the
 * loading / error / empty states.
 */
class PagedGrid(
    root: View,
    onOpen: (Media) -> Unit,
    private val onRetry: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    onRemove: ((Media) -> Unit)? = null,
    private val emptyMessage: String = "No results found. Try a different search or filter.",
) {
    private val grid: GridView = root.findViewById(R.id.media_grid)
    private val loadingMore: View = root.findViewById(R.id.grid_loading_more)
    private val state: View = root.findViewById(R.id.grid_state)
    private val adapter = MediaGridAdapter(onOpen, onRemove)
    private val basePaddingBottom = grid.paddingBottom

    init {
        grid.adapter = adapter
        // Scrolling the results hides the keyboard, so search results aren't half covered.
        grid.onNearEnd(onScrollStart = { grid.hideKeyboard() }) { onLoadMore?.invoke() }
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

    fun scrollToTop() = grid.setSelection(0)

    /** Extra room at the bottom (e.g. for the floating tab bar), in px. */
    fun setBottomSpace(px: Int) {
        grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, basePaddingBottom + px)
        (loadingMore.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin = px
    }
}
