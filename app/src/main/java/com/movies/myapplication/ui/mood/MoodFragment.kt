package com.movies.myapplication.ui.mood

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.movies.myapplication.R
import com.movies.myapplication.appContainer
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.data.model.Mood
import com.movies.myapplication.ui.common.CategoryList
import com.movies.myapplication.ui.common.CategoryRequest
import com.movies.myapplication.ui.common.Navigator
import com.movies.myapplication.ui.common.addMediaRow
import com.movies.myapplication.ui.common.dp
import com.movies.myapplication.ui.common.inflate
import com.movies.myapplication.ui.common.onSystemBars
import com.movies.myapplication.ui.common.padForSystemBars
import com.movies.myapplication.ui.common.show
import com.movies.myapplication.ui.main.TabFragment
import kotlinx.coroutines.launch

/** Mood tab: pick moods from a compact grid, then open matching movies and shows. */
class MoodFragment : TabFragment(R.layout.tab_mood) {

    private lateinit var store: MoodStore

    private lateinit var root: View
    private lateinit var scroll: ScrollView
    private lateinit var subtitle: TextView
    private lateinit var clear: View
    private lateinit var findButton: Button
    private lateinit var rows: LinearLayout
    private lateinit var scrim: View
    private val tiles = mutableMapOf<Mood, View>()

    private var renderedPopular: Pair<List<Media>, List<Media>>? = null
    private var findButtonVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MoodStore(requireContext().appContainer.mediaRepository)
    }

    override fun onTabViewCreated(view: View) {
        root = view
        scroll = view.findViewById(R.id.mood_scroll)
        subtitle = view.findViewById(R.id.mood_subtitle)
        clear = view.findViewById(R.id.mood_clear)
        findButton = view.findViewById(R.id.mood_find)
        rows = view.findViewById(R.id.mood_rows)
        scrim = view.findViewById(R.id.mood_status_scrim)
        // A new view starts empty, so render() fills everything again.
        tiles.clear()
        renderedPopular = null
        findButtonVisible = false

        view.findViewById<View>(R.id.mood_header).padForSystemBars(top = true)
        scrim.onSystemBars { bars -> scrim.layoutParams = scrim.layoutParams.apply { height = bars.top + view.dp(16) } }
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val target = if (y > 0) 1f else 0f
            if (scrim.alpha != target) scrim.animate().alpha(target).setDuration(150).start()
        }
        buildGrid(view)
        clear.setOnClickListener { store.onIntent(MoodIntent.ClearMoods) }
        findButton.setOnClickListener { store.onIntent(MoodIntent.Apply) }

        viewScope.launch { store.state.collect { render(it) } }
        viewScope.launch {
            store.effects.collect { effect ->
                when (effect) {
                    is MoodEffect.OpenResults -> Navigator.moodResults(requireActivity(), effect.moods)
                }
            }
        }
    }

    /**
     * All moods as equal tiles, four per row on phones (eight on wide screens), so the 16 moods fill
     * complete rows and the whole set fits on one screen.
     */
    private fun buildGrid(view: View) {
        val grid: LinearLayout = view.findViewById(R.id.mood_grid)
        grid.removeAllViews()
        val columns = if (resources.configuration.screenWidthDp >= WIDE_SCREEN_DP) COLUMNS_WIDE else COLUMNS
        Mood.entries.chunked(columns).forEachIndexed { rowIndex, moods ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (rowIndex > 0) topMargin = view.dp(8)
            })
            moods.forEachIndexed { index, mood ->
                val tile = row.inflate(R.layout.item_mood_tile)
                // Match the row's tallest tile, so a two-line label doesn't leave its neighbours shorter.
                (tile.layoutParams as LinearLayout.LayoutParams).apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    if (index > 0) marginStart = view.dp(8)
                }
                tile.findViewById<TextView>(R.id.mood_emoji).text = mood.emoji
                tile.findViewById<TextView>(R.id.mood_label).text = mood.label
                tile.setOnClickListener { store.onIntent(MoodIntent.ToggleMood(mood)) }
                row.addView(tile)
                tiles[mood] = tile
            }
            // Keep a short last row's tiles the same width as the others.
            repeat(columns - moods.size) {
                row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = view.dp(8) })
            }
        }
    }

    private fun render(state: MoodUiState) {
        val context = requireContext()
        val selected = state.selected
        tiles.forEach { (mood, tile) ->
            val isSelected = mood in selected
            tile.setBackgroundResource(if (isSelected) R.drawable.bg_tile_selected else R.drawable.bg_tile)
            tile.findViewById<TextView>(R.id.mood_label)
                .setTextColor(context.getColor(if (isSelected) R.color.primary else R.color.text_primary))
            tile.findViewById<View>(R.id.mood_check).show(isSelected)
            // Read as a checkbox: "Happy: Feel-good comedies, checked".
            tile.contentDescription = "${mood.label}: ${mood.hint}, ${if (isSelected) "selected" else "not selected"}"
        }

        // Spell out what the picked moods will find, in the grid's order so the line doesn't reshuffle.
        subtitle.text = if (selected.isEmpty()) {
            "Pick one or more moods"
        } else {
            Mood.entries.filter { it in selected }.joinToString("  ·  ") { it.hint }
        }
        subtitle.setTextColor(context.getColor(if (selected.isEmpty()) R.color.text_secondary else R.color.primary))
        clear.show(selected.isNotEmpty())

        if (selected.isNotEmpty()) {
            findButton.text = if (selected.size == 1) "Find movies & shows" else "Find movies & shows · ${selected.size} moods"
        }
        if (findButtonVisible != selected.isNotEmpty()) animateFindButton(selected.isNotEmpty())

        val popular = state.popularMovies to state.popularTv
        if (popular != renderedPopular) {
            renderedPopular = popular
            rows.removeAllViews()
            val activity = requireActivity()
            val open: (Media) -> Unit = { Navigator.details(activity, it.id, it.type) }
            rows.addMediaRow("Popular Movies", state.popularMovies, open) {
                Navigator.category(activity, CategoryRequest(MediaType.MOVIE, "Popular Movies", CategoryList.POPULAR))
            }
            rows.addMediaRow("Popular TV Shows", state.popularTv, open) {
                Navigator.category(activity, CategoryRequest(MediaType.TV, "Popular TV Shows", CategoryList.POPULAR))
            }
        }
    }

    /** Slides the floating button up into view, or back down out of it. */
    private fun animateFindButton(visible: Boolean) {
        findButtonVisible = visible
        findButton.animate().cancel()
        if (visible) {
            findButton.show(true)
            findButton.translationY = root.dp(80).toFloat()
            findButton.alpha = 0f
            findButton.animate().translationY(0f).alpha(1f).setDuration(200).start()
        } else {
            findButton.animate().translationY(root.dp(80).toFloat()).alpha(0f).setDuration(150)
                .withEndAction { findButton.show(false) }.start()
        }
    }

    override fun onScrollToTop() = scroll.scrollTo(0, 0)

    override fun applyBottomSpace(px: Int) {
        (findButton.layoutParams as FrameLayout.LayoutParams).bottomMargin = px
        findButton.requestLayout()
        // Room for the floating button as well as the tab bar.
        scroll.setPadding(0, 0, 0, px + root.dp(72))
    }

    override fun onDestroy() {
        store.clear()
        super.onDestroy()
    }

    private companion object {
        const val COLUMNS = 4
        const val COLUMNS_WIDE = 8
        const val WIDE_SCREEN_DP = 840
    }
}
