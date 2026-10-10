package com.movies.moviexplore.ui.common

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

/**
 * The one RecyclerView adapter used by every list and row in the app.
 *
 *     val adapter = ItemAdapter<Episode>(R.layout.item_episode_row, key = { it.id }) { view, episode ->
 *         view.findViewById<TextView>(R.id.episode_row_name).text = episode.name
 *     }
 *     recyclerView.adapter = adapter
 *     adapter.submit(episodes)
 *
 * [layout] is one row's XML and [bind] fills a row with its item. [key] tells items apart, so a new list
 * only redraws the rows that changed and keeps the scroll position.
 */
class ItemAdapter<T : Any>(
    private val layout: Int,
    key: (T) -> Any,
    private val bind: (view: View, item: T) -> Unit,
) : ListAdapter<T, RecyclerView.ViewHolder>(diffBy(key)) {

    /** Shows [items]; [onShown] runs once they are on screen (e.g. to scroll to one of them). */
    fun submit(items: List<T>, onShown: (() -> Unit)? = null) {
        if (items == currentList) onShown?.invoke() else submitList(items, onShown)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        object : RecyclerView.ViewHolder(parent.inflate(layout)) {}

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = bind(holder.itemView, getItem(position))

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        // A changed row (e.g. a newly selected option) is redrawn in place, without a cross-fade flicker.
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }
}

/** Scrolls so the item at [position] is the first one in view (like ListView's setSelection). */
fun RecyclerView.scrollToTop(position: Int) {
    (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
}

private fun <T : Any> diffBy(key: (T) -> Any) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(old: T, new: T) = key(old) == key(new)

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(old: T, new: T) = old == new
}
