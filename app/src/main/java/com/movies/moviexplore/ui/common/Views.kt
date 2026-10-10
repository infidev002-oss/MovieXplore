package com.movies.moviexplore.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.model.Credit
import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.data.model.Video

/** Fills an `item_poster` card with [media]. */
fun View.bindPoster(media: Media, onClick: (Media) -> Unit) {
    ImageLoader.load(findViewById(R.id.poster_image), TmdbImage.poster(media.posterPath))
    findViewById<TextView>(R.id.poster_title).text = media.title
    findViewById<TextView>(R.id.poster_meta).text = listOfNotNull(media.year, media.genre).joinToString("  ·  ")
    findViewById<View>(R.id.poster_rating).apply {
        show(media.rating > 0)
        findViewById<TextView>(R.id.rating_value).text = formatRating(media.rating)
    }
    contentDescription = if (media.rating > 0) "${media.title}, rated ${formatRating(media.rating)}" else media.title
    setOnClickListener { onClick(media) }
}

/** Fills a `view_rating` (star and number). */
fun View.bindRating(value: Double) {
    findViewById<TextView>(R.id.rating_value).text = formatRating(value)
}

/** Adds a titled header to [this]; with [onSeeAll], tapping the header opens the full list. */
fun ViewGroup.addSectionHeader(title: String, onSeeAll: (() -> Unit)? = null): View =
    inflate(R.layout.view_section_header).also { header ->
        header.findViewById<TextView>(R.id.section_title).text = title
        if (onSeeAll != null) {
            // The whole header is the tap target, but only the "See all" pill reacts (see view_section_header).
            header.findViewById<View>(R.id.section_chevron).show(true)
            header.contentDescription = "$title, see all"
            header.setOnClickListener { onSeeAll() }
        }
        addView(header)
    }

/** Adds an empty sideways-scrolling row (a horizontal RecyclerView) to [this] and returns it. */
fun ViewGroup.addHorizontalRow(): RecyclerView {
    val row = inflate(R.layout.view_horizontal_row) as RecyclerView
    addView(row)
    return row
}

/** Adds a sideways row showing [items], each row item drawn from [layout] by [bind]. */
private fun <T : Any> ViewGroup.addItemsRow(items: List<T>, layout: Int, key: (T) -> Any, bind: (View, T) -> Unit) {
    val adapter = ItemAdapter(layout, key, bind)
    addHorizontalRow().adapter = adapter
    adapter.submit(items)
}

/** A titled row of poster cards. Nothing is added for an empty list. */
fun ViewGroup.addMediaRow(title: String, items: List<Media>, onOpen: (Media) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    addSectionHeader(title, onSeeAll)
    addItemsRow(items, R.layout.item_poster, key = { it.key }) { card, media ->
        // item_poster fills a grid column; in a row it gets a fixed width.
        card.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = dimen(R.dimen.row_poster_width)
            marginEnd = dimen(R.dimen.row_poster_gap)
        }
        card.bindPoster(media, onOpen)
    }
}

/** A titled row of round cast/crew photos. */
fun ViewGroup.addCreditsRow(title: String, credits: List<Credit>, onOpen: (Int) -> Unit) {
    if (credits.isEmpty()) return
    addSectionHeader(title)
    addItemsRow(credits, R.layout.item_credit, key = { it }) { item, credit ->
        item.findViewById<View>(R.id.credit_photo_frame).clipToOutline = true
        ImageLoader.load(item.findViewById(R.id.credit_photo), TmdbImage.profile(credit.profilePath))
        item.findViewById<TextView>(R.id.credit_name).text = credit.name
        item.findViewById<TextView>(R.id.credit_role).text = credit.role
        item.contentDescription = "${credit.name}, ${credit.role}"
        item.setOnClickListener { onOpen(credit.id) }
    }
}

/** The "Trailers & Teasers" row; tapping one plays it on YouTube. */
fun ViewGroup.addTrailersRow(videos: List<Video>) {
    if (videos.isEmpty()) return
    addSectionHeader("Trailers & Teasers")
    addItemsRow(videos, R.layout.item_video, key = { it.key }) { item, video ->
        ImageLoader.load(item.findViewById(R.id.thumbnail_image), video.thumbnailUrl)
        item.findViewById<TextView>(R.id.video_name).text = video.name
        item.findViewById<TextView>(R.id.video_type).text = video.type
        item.contentDescription = "Play ${video.name}"
        item.setOnClickListener { it.context.playOnYouTube(video) }
    }
}

/**
 * Drives a `view_state` overlay: a spinner while [loading], a [message] (with Retry when [onRetry] is
 * given), or nothing once content is showing.
 */
fun View.renderState(loading: Boolean, message: String?, onRetry: (() -> Unit)? = null) {
    show(loading || message != null)
    findViewById<View>(R.id.state_progress).show(loading)
    findViewById<View>(R.id.state_message_group).show(!loading && message != null)
    findViewById<TextView>(R.id.state_message).text = message
    findViewById<Button>(R.id.state_retry).apply {
        show(onRetry != null)
        setOnClickListener { onRetry?.invoke() }
    }
}

/** Sets up a `view_top_bar`: title, back action, and room for the status bar. */
fun View.bindTopBar(title: String, onBack: (() -> Unit)?) {
    findViewById<TextView>(R.id.top_bar_title).text = title
    findViewById<View>(R.id.top_bar_back).apply {
        show(onBack != null)
        setOnClickListener { onBack?.invoke() }
    }
    if (onBack == null) findViewById<TextView>(R.id.top_bar_title).setPadding(dimen(R.dimen.top_bar_title_start), 0, 0, 0)
    padForSystemBars(top = true)
}

/** Moves a floating view (like the round back button) below the status bar. */
fun View.placeBelowStatusBar(extraTopMargin: Int = dimen(R.dimen.below_status_bar_gap)) {
    onSystemBars { bars ->
        (layoutParams as ViewGroup.MarginLayoutParams).topMargin = bars.top + extraTopMargin
        requestLayout()
    }
}

/** Shows a long text collapsed to [collapsedLines], with a "Read More" / "Show Less" toggle when it overflows. */
fun TextView.makeExpandable(toggle: TextView, collapsedLines: Int) {
    var expanded = false
    maxLines = collapsedLines
    toggle.show(false)
    post {
        val overflows = layout?.let { it.lineCount > collapsedLines || (it.lineCount > 0 && it.getEllipsisCount(it.lineCount - 1) > 0) } == true
        toggle.show(overflows)
    }
    toggle.setOnClickListener {
        expanded = !expanded
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines
        toggle.text = if (expanded) "Show Less" else "Read More"
    }
}
