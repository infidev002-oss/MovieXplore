package com.movies.moviexplore.data.model

import com.movies.moviexplore.watchlist.WatchlistItem

enum class MediaType(val path: String, val label: String) {
    MOVIE("movie", "Movie"),
    TV("tv", "TV Show"),
}

data class Media(
    val id: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String,
    val year: String?,
    val rating: Double,
    val genre: String?,
) {
    /** Same as the watchlist uniqueId, e.g. movie_550. */
    val key: String get() = WatchlistItem.key(type.path, id.toString())
}

data class Genre(val id: Int, val name: String)

data class Provider(val id: Int, val name: String)

/** A country, by ISO 3166-1 code. */
data class Country(val code: String, val name: String) {
    /** The country's flag emoji, built from its two regional-indicator letters. */
    val flag: String
        get() = if (code.length == 2 && code.all { it in 'A'..'Z' }) {
            code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        } else {
            "🏳️"
        }
}

/** How a service offers a title, in the order offers are shown. */
enum class OfferType(val label: String) {
    STREAM("Stream"),
    FREE("Free"),
    ADS("With Ads"),
    RENT("Rent"),
    BUY("Buy"),
}

data class WatchOffer(val providerId: Int, val providerName: String, val logoPath: String?, val type: OfferType)

/** Where a title can be watched in one country; [link] is TMDB's watch page for that country. */
data class WatchAvailability(val link: String?, val offers: List<WatchOffer>)

data class Video(val key: String, val name: String, val type: String) {
    val url: String get() = "https://www.youtube.com/watch?v=$key"
    val thumbnailUrl: String get() = "https://img.youtube.com/vi/$key/hqdefault.jpg"
}

data class Credit(val id: Int, val name: String, val role: String, val profilePath: String?)

data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int)

data class Episode(
    val id: Int,
    val number: Int,
    val name: String,
    val stillPath: String?,
    val runtime: Int?,
    val airDate: String?,
) {
    /** The episode's own still, or the show's backdrop when TMDB has none yet (common for unaired episodes). */
    fun stillUrl(showBackdropPath: String?): String? = TmdbImage.still(stillPath) ?: TmdbImage.backdrop(showBackdropPath)
}

data class EpisodeDetails(
    val season: Int,
    val number: Int,
    val name: String,
    val overview: String,
    val stillPath: String?,
    val airDate: String?,
    val runtime: Int?,
    val rating: Double,
    val credits: List<Credit>,
    val videos: List<Video>,
)

data class MediaDetails(
    val media: Media,
    val genres: List<String>,
    val country: String?,
    val videos: List<Video>,
    val credits: List<Credit>,
    val seasons: List<SeasonInfo>,
    val similar: List<Media>,
) {
    val watchUrl: String get() = "https://www.themoviedb.org/${media.type.path}/${media.id}/watch"
}

data class Person(
    val id: Int,
    val name: String,
    val biography: String,
    val birthday: String?,
    val placeOfBirth: String?,
    val profilePath: String?,
    val movies: List<Media>,
    val tvShows: List<Media>,
)

data class PageResult(val items: List<Media>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

object TmdbImage {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?) = path?.let { "${BASE}w342$it" }
    fun posterLarge(path: String?) = path?.let { "${BASE}w780$it" }
    fun backdrop(path: String?) = path?.let { "${BASE}w780$it" }
    fun profile(path: String?) = path?.let { "${BASE}w185$it" }
    fun logo(path: String?) = path?.let { "${BASE}w154$it" }
    fun still(path: String?) = path?.let { "${BASE}w300$it" }
    fun stillLarge(path: String?) = path?.let { "${BASE}w780$it" }
}
