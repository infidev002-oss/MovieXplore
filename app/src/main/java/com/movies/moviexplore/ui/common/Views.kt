package com.movies.moviexplore.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

/** Adds an empty sideways-scrolling row to [this] and returns the container its cards go into. */
fun ViewGroup.addHorizontalRow(): LinearLayout {
    val row = inflate(R.layout.view_horizontal_row)
    addView(row)
    return row.findViewById(R.id.row_items)
}

/** A titled row of poster cards. Nothing is added for an empty list. */
fun ViewGroup.addMediaRow(title: String, items: List<Media>, onOpen: (Media) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    addSectionHeader(title, onSeeAll)
    val row = addHorizontalRow()
    items.forEach { media ->
        val card = row.inflate(R.layout.item_poster)
        card.layoutParams = LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) }
        card.bindPoster(media, onOpen)
        row.addView(card)
    }
}

/** A titled row of round cast/crew photos. */
fun ViewGroup.addCreditsRow(title: String, credits: List<Credit>, onOpen: (Int) -> Unit) {
    if (credits.isEmpty()) return
    addSectionHeader(title)
    val row = addHorizontalRow()
    credits.forEach { credit ->
        val item = row.inflate(R.layout.item_credit)
        item.findViewById<View>(R.id.credit_photo_frame).clipToOutline = true
        ImageLoader.load(item.findViewById(R.id.credit_photo), TmdbImage.profile(credit.profilePath))
        item.findViewById<TextView>(R.id.credit_name).text = credit.name
        item.findViewById<TextView>(R.id.credit_role).text = credit.role
        item.contentDescription = "${credit.name}, ${credit.role}"
        item.setOnClickListener { onOpen(credit.id) }
        row.addView(item)
    }
}

/** The "Trailers & Teasers" row; tapping one plays it on YouTube. */
fun ViewGroup.addTrailersRow(videos: List<Video>) {
    if (videos.isEmpty()) return
    addSectionHeader("Trailers & Teasers")
    val row = addHorizontalRow()
    videos.forEach { video ->
        val item = row.inflate(R.layout.item_video)
        ImageLoader.load(item.findViewById(R.id.thumbnail_image), video.thumbnailUrl)
        item.findViewById<TextView>(R.id.video_name).text = video.name
        item.findViewById<TextView>(R.id.video_type).text = video.type
        item.contentDescription = "Play ${video.name}"
        item.setOnClickListener { it.context.playOnYouTube(video) }
        row.addView(item)
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
    if (onBack == null) findViewById<TextView>(R.id.top_bar_title).setPadding(dp(12), 0, 0, 0)
    padForSystemBars(top = true)
}

/** Moves a floating view (like the round back button) below the status bar. */
fun View.placeBelowStatusBar(extraTopMargin: Int = dp(12)) {
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
