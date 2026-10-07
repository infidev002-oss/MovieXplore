package com.movies.moviexplore.ui.browse

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.FilterOptions
import com.movies.moviexplore.data.model.Genre
import com.movies.moviexplore.data.model.Provider
import com.movies.moviexplore.data.model.SortOption
import com.movies.moviexplore.ui.common.Option
import com.movies.moviexplore.ui.common.OptionAdapter
import com.movies.moviexplore.ui.common.centerStartIcon
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.padForSystemBars
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.showAppToast
import com.movies.moviexplore.ui.common.tickHaptic

private enum class FilterField(val label: String) {
    YEAR("Year"), GENRE("Genre"), SORT("Sort By"), REGION("Region"), LANGUAGE("Language"), CHANNEL("Channels")
}

private class FilterChoice(val label: String, val selected: Boolean, val apply: (DiscoverFilter) -> DiscoverFilter)

/**
 * Drives the `panel_filter` drawer. It edits a draft copy of the filter: a summary of every field, or
 * the choices for one field. Only Apply reports the draft back.
 */
class FilterPanel(
    private val overlay: View,
    private val onApply: (DiscoverFilter) -> Unit,
    private val onDismiss: () -> Unit,
    private val onRegionChange: (String?) -> Unit,
) {
    private val panel: View = overlay.findViewById(R.id.filter_panel)
    private val title: TextView = overlay.findViewById(R.id.filter_title)
    private val back: View = overlay.findViewById(R.id.filter_back)
    private val summary: View = overlay.findViewById(R.id.filter_summary)
    private val fields: LinearLayout = overlay.findViewById(R.id.filter_fields)
    private val optionList: ListView = overlay.findViewById(R.id.filter_options)
    private val actions: View = overlay.findViewById(R.id.filter_actions)
    private val reset: View = overlay.findViewById(R.id.filter_reset)
    private val optionAdapter = OptionAdapter()

    private var draft = DiscoverFilter()
    private var genres: List<Genre> = emptyList()
    private var providers: List<Provider> = emptyList()
    private var isSearching = false
    private var editing: FilterField? = null
    private var choices: List<FilterChoice> = emptyList()

    val isOpen: Boolean get() = overlay.visibility == View.VISIBLE

    init {
        panel.padForSystemBars(top = true, bottom = true)
        panel.layoutParams = panel.layoutParams.apply {
            width = (overlay.resources.displayMetrics.widthPixels * 0.84f).toInt()
        }
        overlay.findViewById<View>(R.id.filter_scrim).setOnClickListener { onDismiss() }
        overlay.findViewById<View>(R.id.filter_close).setOnClickListener { onDismiss() }
        back.setOnClickListener { showSummary() }
        overlay.findViewById<Button>(R.id.filter_apply).apply {
            centerStartIcon()
            setOnClickListener { onApply(draft) }
        }
        reset.setOnClickListener {
            if (draft.isDefault) {
                // Nothing to reset: a short shake says the tap landed but there was nothing to do.
                it.animate().cancel()
                it.translationX = 0f
                ObjectAnimator.ofFloat(it, View.TRANSLATION_X, 0f, -8f, 8f, -6f, 6f, -3f, 0f)
                    .apply { duration = 360 }.start()
                it.showAppToast("Filters are already at default", R.drawable.ic_info, above = actions)
                return@setOnClickListener
            }
            draft = DiscoverFilter()
            onRegionChange(draft.region)
            showSummary()
            // Resetting only changes the draft, so make the press obvious: a spin, a tick and a hint to Apply.
            it.tickHaptic()
            it.rotation = 0f
            it.animate().rotation(-360f).setDuration(500).setInterpolator(DecelerateInterpolator()).withEndAction { it.rotation = 0f }.start()
            it.showAppToast("Filters reset. Tap Apply to update the list.", R.drawable.ic_refresh, above = actions)
        }
        optionList.adapter = optionAdapter
        optionList.setOnItemClickListener { _, _, position, _ ->
            val regionBefore = draft.region
            draft = choices[position].apply(draft)
            if (draft.region != regionBefore) onRegionChange(draft.region)
            showSummary()
        }
    }

    /** Opens the drawer on a fresh draft of [filter]. */
    fun open(filter: DiscoverFilter) {
        if (isOpen) return
        draft = filter
        showSummary()
        overlay.show(true)
        panel.translationX = panel.layoutParams.width.toFloat()
        panel.animate().translationX(0f).setDuration(220).start()
        onRegionChange(draft.region)
    }

    fun close() {
        if (!isOpen) return
        panel.animate().translationX(panel.width.toFloat()).setDuration(180).withEndAction { overlay.show(false) }.start()
    }

    /** Back inside the drawer: from a field's choices to the summary, otherwise close. */
    fun back() = if (editing != null) showSummary() else onDismiss()

    /** Latest genres, channels and search mode from the screen's state. */
    fun update(genres: List<Genre>, providers: List<Provider>, isSearching: Boolean) {
        if (genres == this.genres && providers == this.providers && isSearching == this.isSearching) return
        this.genres = genres
        this.providers = providers
        this.isSearching = isSearching
        val field = editing
        if (field == null) showSummary() else showChoices(field)
    }

    private fun showSummary() {
        editing = null
        title.text = "Filter"
        back.show(false)
        summary.show(true)
        actions.show(true)
        optionList.show(false)
        // Dimmed when every field is already at its default (still tappable, to explain why nothing happens).
        reset.alpha = if (draft.isDefault) 0.45f else 1f
        fields.removeAllViews()
        if (isSearching) {
            fields.addView(TextView(fields.context).apply {
                setTextAppearance(R.style.Text_BodySmall)
                text = "While searching, filters apply to the search results. A channel clears the search (channels need the full list)."
                setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
        FilterField.entries.forEach { field ->
            val item = fields.inflate(R.layout.item_filter_field)
            item.findViewById<TextView>(R.id.field_label).text = field.label
            item.findViewById<TextView>(R.id.field_value).text = valueLabel(field)
            item.findViewById<View>(R.id.field_row).setOnClickListener { showChoices(field) }
            fields.addView(item)
        }
    }

    private fun showChoices(field: FilterField) {
        editing = field
        title.text = field.label
        back.show(true)
        summary.show(false)
        actions.show(false)
        optionList.show(true)
        choices = choicesFor(field)
        optionAdapter.submit(choices.map { Option(it.label, it.selected) })
        optionList.setSelection(choices.indexOfFirst { it.selected }.coerceAtLeast(0))
    }

    private fun valueLabel(field: FilterField): String = when (field) {
        FilterField.YEAR -> draft.year?.toString() ?: "Default"
        FilterField.GENRE -> when (draft.genreIds.size) {
            0 -> "All Genres"
            1 -> genres.firstOrNull { it.id in draft.genreIds }?.name ?: "1 genre"
            else -> "${draft.genreIds.size} genres"
        }
        FilterField.SORT -> draft.sort.label
        FilterField.REGION -> FilterOptions.regionName(draft.region) ?: "Global"
        FilterField.LANGUAGE -> FilterOptions.languageName(draft.language) ?: "All Languages"
        FilterField.CHANNEL -> draft.providerId?.let { id -> providers.firstOrNull { it.id == id }?.name ?: "Selected channel" }
            ?: "All Channels"
    }

    private fun choicesFor(field: FilterField): List<FilterChoice> = when (field) {
        FilterField.YEAR -> listOf(FilterChoice("Default", draft.year == null) { it.copy(year = null) }) +
            FilterOptions.years.map { year -> FilterChoice(year.toString(), draft.year == year) { it.copy(year = year) } }
        FilterField.GENRE -> listOf(FilterChoice("All Genres", draft.genreIds.isEmpty()) { it.copy(genreIds = emptySet()) }) +
            genres.map { genre ->
                FilterChoice(genre.name, draft.genreIds == setOf(genre.id)) { it.copy(genreIds = setOf(genre.id), matchAnyGenre = false) }
            }
        FilterField.SORT -> SortOption.entries.map { sort -> FilterChoice(sort.label, draft.sort == sort) { it.copy(sort = sort) } }
        FilterField.REGION -> listOf(FilterChoice("Global", draft.region == null) { it.copy(region = null) }) +
            FilterOptions.regions.map { (code, name) -> FilterChoice(name, draft.region == code) { it.copy(region = code) } }
        FilterField.LANGUAGE -> listOf(FilterChoice("All Languages", draft.language == null) { it.copy(language = null) }) +
            FilterOptions.languages.map { (code, name) -> FilterChoice(name, draft.language == code) { it.copy(language = code) } }
        FilterField.CHANNEL -> listOf(FilterChoice("All Channels", draft.providerId == null) { it.copy(providerId = null) }) +
            providers.map { provider -> FilterChoice(provider.name, draft.providerId == provider.id) { it.copy(providerId = provider.id) } }
    }
}
