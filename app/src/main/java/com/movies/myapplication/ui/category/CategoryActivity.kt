package com.movies.myapplication.ui.category

import android.os.Bundle
import android.view.View
import com.movies.myapplication.R
import com.movies.myapplication.appContainer
import com.movies.myapplication.ui.common.BaseActivity
import com.movies.myapplication.ui.common.CategoryList
import com.movies.myapplication.ui.common.CategoryRequest
import com.movies.myapplication.ui.common.Navigator
import com.movies.myapplication.ui.common.PagedGrid
import com.movies.myapplication.ui.common.bindTopBar
import com.movies.myapplication.ui.common.mediaType
import com.movies.myapplication.ui.common.onSystemBars

/** A category's titled poster grid, e.g. "Action" or "Trending Movies", loading more as you scroll. */
class CategoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)

        val request = CategoryRequest(
            type = intent.mediaType(),
            title = intent.getStringExtra(Navigator.EXTRA_TITLE).orEmpty(),
            list = CategoryList.valueOf(intent.getStringExtra(Navigator.EXTRA_LIST) ?: CategoryList.POPULAR.name),
            genreId = intent.getIntExtra(Navigator.EXTRA_GENRE, 0),
        )
        val store = CategoryStore(appContainer.mediaRepository, request).attach()

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
    }
}
