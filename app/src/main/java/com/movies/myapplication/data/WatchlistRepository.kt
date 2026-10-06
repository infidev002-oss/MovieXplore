package com.movies.myapplication.data

import android.content.SharedPreferences
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Saved titles, newest first, stored as one JSON array in SharedPreferences. */
class WatchlistRepository(private val prefs: SharedPreferences) {

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<Media>> = _items.asStateFlow()

    private val _keys = MutableStateFlow(_items.value.mapTo(HashSet()) { it.key })
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun toggle(media: Media) {
        val current = _items.value
        save(if (current.any { it.key == media.key }) current.filterNot { it.key == media.key } else listOf(media) + current)
    }

    fun clear() = save(emptyList())

    private fun save(list: List<Media>) {
        _items.value = list
        _keys.value = list.mapTo(HashSet()) { it.key }
        prefs.edit().putString(KEY, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun read(): List<Media> = runCatching {
        val array = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toMedia() }
    }.getOrDefault(emptyList())

    private fun Media.toJson() = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("title", title)
        .put("posterPath", posterPath)
        .put("backdropPath", backdropPath)
        .put("overview", overview)
        .put("year", year)
        .put("rating", rating)
        .put("genre", genre)

    private fun JSONObject.toMedia(): Media? = runCatching {
        Media(
            id = getInt("id"),
            type = MediaType.valueOf(getString("type")),
            title = optString("title"),
            posterPath = str("posterPath"),
            backdropPath = str("backdropPath"),
            overview = optString("overview"),
            year = str("year"),
            rating = optDouble("rating", 0.0),
            genre = str("genre"),
        )
    }.getOrNull()

    private companion object {
        const val KEY = "watchlist"
    }
}
