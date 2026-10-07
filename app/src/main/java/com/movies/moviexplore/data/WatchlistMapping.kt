package com.movies.moviexplore.data

import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.watchlist.WatchlistItem
import com.movies.moviexplore.watchlist.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The kit's watchlist entry for this title. Only id, type, title and poster are stored (AGENTS.md §8). */
fun Media.toWatchlistItem() = WatchlistItem(
    uniqueId = key,
    id = id.toString(),
    title = title,
    type = type.path,
    posterPath = posterPath.orEmpty(),
)

/** A saved entry as a card; fields the watchlist doesn't store stay empty. Null for an unknown type or id. */
fun WatchlistItem.toMedia(): Media? {
    val mediaType = MediaType.entries.firstOrNull { it.path == type } ?: return null
    return Media(
        id = id.toIntOrNull() ?: return null,
        type = mediaType,
        title = title,
        posterPath = posterPath.ifEmpty { null },
        backdropPath = null,
        overview = "",
        year = null,
        rating = 0.0,
        genre = null,
    )
}

/** [Media.key] of every saved title, for the bookmark state on cards. */
fun savedWatchlistKeys(): Flow<Set<String>> =
    WatchlistRepository.observeAll().map { items -> items.mapTo(HashSet()) { it.uniqueId } }
