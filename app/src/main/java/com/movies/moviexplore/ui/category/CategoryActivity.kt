package com.movies.moviexplore.ui.category

import android.os.Bundle
import android.view.View
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.NativeAdRowAdapter
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
    private var nativeRow: NativeAdRowAdapter? = null   // genre lists only

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
        // The category ads (native first in the grid, banner at the bottom) belong to a selected genre's list
        // only, not to Trending / Popular / Top Rated.
        val isGenreList = request.list == CategoryList.GENRE
        if (isGenreList) nativeRow = NativeAdRowAdapter(this, categoryNative, RemoteValues.NATIVE_CATEGORY, NativeSize.MEDIUM)

        findViewById<View>(R.id.top_bar).bindTopBar(request.title, onBack = ::finish)
        val gridRoot = findViewById<View>(R.id.screen_grid)
        val grid = PagedGrid(
            root = gridRoot,
            onOpen = { Navigator.details(this, it.id, it.type) },
            onRetry = { store.onIntent(CategoryIntent.Retry) },
            onLoadMore = { store.onIntent(CategoryIntent.LoadMore) },
            nativeRow = nativeRow,
        )
        gridRoot.onSystemBars { bars -> grid.setBottomSpace(bars.bottom) }

        store.state.observe { grid.render(it.items, it.isLoading, it.isLoadingMore, it.error) }

        if (isGenreList) bindBanner(this, RemoteValues.BANNER_CAT, findViewById(R.id.banner_root))
    }

    override fun onResume() {
        super.onResume()
        nativeRow?.onScreenVisible()   // one native refresh per visit
    }
}
