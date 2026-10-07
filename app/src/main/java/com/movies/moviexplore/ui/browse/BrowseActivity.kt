package com.movies.moviexplore.ui.browse

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.Genre
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.CategoryRequest
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.PagedGrid
import com.movies.moviexplore.ui.common.bindTopBar
import com.movies.moviexplore.ui.common.dp
import com.movies.moviexplore.ui.common.hideKeyboard
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.mediaType
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.toast

/** Search screen for movies or TV shows: search box, filters, and genre chips that open a category. */
class BrowseActivity : BaseActivity() {

    private lateinit var store: BrowseStore
    private lateinit var filterPanel: FilterPanel
    private var renderedGenres: List<Genre>? = null
    private var filtersShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        bindBanner(this, RemoteValues.BANNER_LIST, findViewById(R.id.banner_root))
        val type = intent.mediaType()
        store = BrowseStore(MediaRepository.shared, type, DiscoverFilter(), loadGenres = true).attach()

        findViewById<View>(R.id.top_bar).bindTopBar(intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty(), onBack = ::finish)

        val input = findViewById<EditText>(R.id.search_input)
        val clear = findViewById<View>(R.id.search_clear)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                store.onIntent(BrowseIntent.QueryChanged(s?.toString().orEmpty()))
            }
        })
        input.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) view.hideKeyboard()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
        clear.setOnClickListener { input.setText("") }
        findViewById<View>(R.id.filter_button).setOnClickListener {
            input.hideKeyboard()
            store.onIntent(BrowseIntent.OpenFilters)
        }

        val gridRoot = findViewById<View>(R.id.screen_grid)
        val grid = PagedGrid(
            root = gridRoot,
            onOpen = { Navigator.details(this, it.id, it.type) },
            onRetry = { store.onIntent(BrowseIntent.Retry) },
            onLoadMore = { store.onIntent(BrowseIntent.LoadMore) },
        )
        gridRoot.onSystemBars { bars -> grid.setBottomSpace(bars.bottom) }

        filterPanel = FilterPanel(
            overlay = findViewById(R.id.filter_overlay),
            onApply = { store.onIntent(BrowseIntent.ApplyFilter(it)) },
            onDismiss = { store.onIntent(BrowseIntent.CloseFilters) },
            onRegionChange = { store.onIntent(BrowseIntent.RegionChanged(it)) },
        )

        store.state.observe { state ->
            clear.show(state.query.isNotEmpty())
            findViewById<View>(R.id.filter_badge).show(!state.filter.isDefault)
            // Each filter narrows the list; several together (e.g. a small channel + a year + a language)
            // can leave nothing, which should read as "too narrow", not as broken.
            val filteredEmpty = if (state.filter.isDefault) null else "No titles match all of these filters. Try removing one, like Channels or Year."
            grid.render(state.items, state.isLoading, state.isLoadingMore, state.error, emptyText = filteredEmpty)
            renderGenres(state.genres, type)
            filterPanel.update(state.genres, state.providers, isSearching = state.query.isNotBlank())
            if (state.showFilters != filtersShown) {
                filtersShown = state.showFilters
                if (state.showFilters) filterPanel.open(state.filter) else filterPanel.close()
                // While the drawer is open, Back steps back inside it instead of leaving the screen.
                interceptBack(if (state.showFilters) ({ filterPanel.back() }) else null)
            }
        }
        store.effects.observe { effect ->
            when (effect) {
                BrowseEffect.SearchClearedForChannel -> {
                    input.setText("")
                    toast("Channels filter the full list, so the search was cleared.")
                }
            }
        }
    }

    private fun renderGenres(genres: List<Genre>, type: MediaType) {
        if (genres == renderedGenres) return
        renderedGenres = genres
        findViewById<View>(R.id.genre_scroll).show(genres.isNotEmpty())
        val chips = findViewById<LinearLayout>(R.id.genre_chips)
        chips.removeAllViews()
        genres.forEach { genre ->
            val chip = chips.inflate(R.layout.item_genre_chip) as TextView
            chip.text = genre.name
            chip.setPillSelected(false)
            chip.setOnClickListener { Navigator.category(this, CategoryRequest.genre(type, genre)) }
            chips.addView(chip, LinearLayout.LayoutParams(chip.layoutParams).apply { marginEnd = dp(10) })
        }
    }
}
