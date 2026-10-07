package com.movies.moviexplore.ui.category

import android.os.Bundle
import android.view.View
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.NativeAds
import com.movies.moviexplore.ads.NativeSize
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.ui.common.BaseActivity
import com.movies.moviexplore.ui.common.CategoryList
import com.movies.moviexplore.ui.common.CategoryRequest
import com.movies.moviexplore.ui.common.Navigator
import com.movies.moviexplore.ui.common.PagedGrid
import com.movies.moviexplore.ui.common.bindBanner
import com.movies.moviexplore.ui.common.bindTopBar
import com.movies.moviexplore.ui.common.mediaType
import com.movies.moviexplore.ui.common.onSystemBars

/** A category's titled poster grid, e.g. "Action" or "Trending Movies", loading more as you scroll. */
class CategoryActivity : BaseActivity() {

    private val categoryNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_CATEGORY) }
    private val isGenreList get() = intent.getStringExtra(Navigator.EXTRA_LIST) == CategoryList.GENRE.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)

        val request = CategoryRequest(
            type = intent.mediaType(),
            title = intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty(),
            list = CategoryList.valueOf(intent.getStringExtra(Navigator.EXTRA_LIST) ?: CategoryList.POPULAR.name),
            genreId = intent.getIntExtra(Navigator.EXTRA_GENRE, 0),
        )
        val store = CategoryStore(MediaRepository.shared, request).attach()

        findViewById<View>(R.id.top_bar).bindTopBar(request.title, onBack = ::finish)
        val gridRoot = findViewById<View>(R.id.screen_grid)
        val grid = PagedGrid(
            root = gridRoot,
            onOpen = { Navigator.details(this, it.id, it.type) },
            onRetry = { store.onIntent(CategoryIntent.Retry) },
            onLoadMore = { store.onIntent(CategoryIntent.LoadMore) },
        )
        gridRoot.onSystemBars { bars -> grid.setBottomSpace(bars.bottom) }

        store.state.observe { grid.render(it.items, it.isLoading, it.isLoadingMore, it.error) }

        bindBanner(this, RemoteValues.BANNER_LIST, findViewById(R.id.banner_root))
        // The category native belongs to a selected genre's list only, not to Trending / Popular / Top Rated.
        if (request.list == CategoryList.GENRE) {
            NativeAds.attach(this, categoryNative, findViewById(R.id.grid_native), RemoteValues.NATIVE_CATEGORY, NativeSize.MEDIUM)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isGenreList) categoryNative.refreshIfNeeded(RemoteValues.NATIVE_CATEGORY)
    }
}
