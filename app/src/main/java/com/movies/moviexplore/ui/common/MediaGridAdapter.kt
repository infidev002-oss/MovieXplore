package com.movies.moviexplore.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.movies.moviexplore.R
import com.movies.moviexplore.data.model.Media

/**
 * Poster cards for a GridView. Rows are recycled by the GridView, so only visible cards exist.
 * With [onRemove], each card shows a filled bookmark that removes it (the Watchlist).
 */
class MediaGridAdapter(
    private val onOpen: (Media) -> Unit,
    private val onRemove: ((Media) -> Unit)? = null,
) : BaseAdapter() {

    private var items: List<Media> = emptyList()

    /** Replaces the list; does nothing when it hasn't changed, so re-renders don't reset the grid. */
    fun submit(newItems: List<Media>) {
        if (newItems == items) return
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount() = items.size

    override fun getItem(position: Int) = items[position]

    override fun getItemId(position: Int) = items[position].key.hashCode().toLong()

    override fun hasStableIds() = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: parent.inflate(R.layout.item_poster)
        val media = items[position]
        view.bindPoster(media, onOpen)
        view.findViewById<View>(R.id.poster_bookmark).apply {
            show(onRemove != null)
            setOnClickListener {
                it.tickHaptic()
                onRemove?.invoke(media)
            }
        }
        return view
    }
}
