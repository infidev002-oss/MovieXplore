package com.movies.moviexplore.ui.browse

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.NativeAdRowAdapter
import com.movies.moviexplore.ads.NativeAds
import com.movies.moviexplore.ads.NativeSize
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.Genre
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.ui.common.dimen
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.CategoryRequest
import com.movies.moviexplore.ui.common.ItemAdapter
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.PagedGrid
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.bindTopBar
import com.movies.moviexplore.ui.common.hideKeyboard
import com.movies.moviexplore.ui.common.mediaType
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.setPillSelected
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.toast

/** Search screen for movies or TV shows: search box, filters, and genre chips that open a category. */
class BrowseActivity : BaseActivity() {

    private lateinit var store: BrowseStore
    private lateinit var type: MediaType

    private lateinit var searchInput: EditText
    private lateinit var searchClear: View
    private lateinit var filterBadge: View
    private lateinit var grid: PagedGrid
    private val categoryNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_CATEGORY) }
    private lateinit var nativeRow: NativeAdRowAdapter
    private lateinit var filterPanel: FilterPanel

    private lateinit var genreChips: RecyclerView
    private var filtersShown = false

    // Genre chips; tapping one opens that genre's category list.
    private val genreAdapter = ItemAdapter<Genre>(R.layout.item_genre_chip, key = { it.id }) { view, genre ->
        val chip = view as TextView
        chip.updateLayoutParams<RecyclerView.LayoutParams> { marginEnd = dimen(R.dimen.chip_gap) }
        chip.text = genre.name
        chip.setPillSelected(false)
        chip.setOnClickListener { Navigator.category(this, CategoryRequest.genre(type, genre)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        type = intent.mediaType()
        store = BrowseStore(MediaRepository.shared, type, DiscoverFilter(), loadGenres = true).attach()

        findViewById<View>(R.id.top_bar).bindTopBar(intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty(), onBack = ::goBack)
        setUpSearch()
        genreChips = findViewById(R.id.genre_chips)
        genreChips.adapter = genreAdapter
        setUpGrid()
        setUpFilters()

        store.state.observe { render(it) }
        store.effects.observe { effect ->
            when (effect) {
                BrowseEffect.SearchClearedForChannel -> {
                    searchInput.setText("")
                    toast("Channels filter the full list, so the search was cleared.")
                }
            }
        }
    }

    /** Search box: every change goes to the store (it debounces); the keyboard's Search key just closes the keyboard. */
    private fun setUpSearch() {
        searchInput = findViewById(R.id.search_input)
        searchClear = findViewById(R.id.search_clear)
        searchInput.doAfterTextChanged { store.onIntent(BrowseIntent.QueryChanged(it?.toString().orEmpty())) }
        searchInput.setOnEditorActionListener { view, actionId, _ ->
            val isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
            if (isSearch) view.hideKeyboard()
            isSearch
        }
        searchClear.setOnClickListener { searchInput.setText("") }
    }

    /** The poster grid with the category native ad as its first item, and the category banner at the bottom. */
    private fun setUpGrid() {
        nativeRow = NativeAdRowAdapter(this, categoryNative, RemoteValues.NATIVE_CATEGORY, NativeSize.MEDIUM)
        val gridRoot = findViewById<View>(R.id.screen_grid)
        grid = PagedGrid(
            root = gridRoot,
            onOpen = { Navigator.details(this, it.id, it.type) },
            onRetry = { store.onIntent(BrowseIntent.Retry) },
            onLoadMore = { store.onIntent(BrowseIntent.LoadMore) },
            nativeRow = nativeRow,
        )
        gridRoot.onSystemBars { bars -> grid.setBottomSpace(bars.bottom) }
        bindBanner(this, RemoteValues.BANNER_CAT, findViewById(R.id.banner_root))
    }

    override fun onResume() {
        super.onResume()
        nativeRow.onScreenVisible()   // one native refresh per visit
    }

    private fun setUpFilters() {
        filterBadge = findViewById(R.id.filter_badge)
        findViewById<View>(R.id.filter_button).setOnClickListener {
            searchInput.hideKeyboard()
            store.onIntent(BrowseIntent.OpenFilters)
        }
        filterPanel = FilterPanel(
            overlay = findViewById(R.id.filter_overlay),
            onApply = { store.onIntent(BrowseIntent.ApplyFilter(it)) },
            onDismiss = { store.onIntent(BrowseIntent.CloseFilters) },
            onRegionChange = { store.onIntent(BrowseIntent.RegionChanged(it)) },
        )
    }

    private fun render(state: BrowseUiState) {
        searchClear.show(state.query.isNotEmpty())
        filterBadge.show(!state.filter.isDefault)

        // Each filter narrows the list; several together (e.g. a small channel + a year + a language)
        // can leave nothing, which should read as "too narrow", not as broken.
        val filteredEmpty = if (state.filter.isDefault) null else "No titles match all of these filters. Try removing one, like Channels or Year."
        grid.render(state.items, state.isLoading, state.isLoadingMore, state.error, emptyText = filteredEmpty)

        renderGenres(state.genres)
        renderFilterPanel(state)
    }

    private fun renderFilterPanel(state: BrowseUiState) {
        filterPanel.update(state.genres, state.providers, isSearching = state.query.isNotBlank())
        if (state.showFilters == filtersShown) return
        filtersShown = state.showFilters
        if (state.showFilters) filterPanel.open(state.filter) else filterPanel.close()
        // While the drawer is open, Back steps back inside it instead of leaving the screen.
        interceptBack(if (state.showFilters) ({ filterPanel.back() }) else null)
    }

    private fun renderGenres(genres: List<Genre>) {
        genreChips.show(genres.isNotEmpty())
        genreAdapter.submit(genres)
    }
}
