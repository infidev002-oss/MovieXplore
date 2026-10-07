package your.app.network.model

import com.google.gson.annotations.SerializedName

data class PagedResponse(
    val page: Int = 0,
    val results: List<MediaItem> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 0
)

data class MediaItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null
) {
    val displayTitle: String get() = title ?: name ?: ""
}

data class MediaDetails(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    val runtime: Int? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    val genres: List<Genre>? = null,
    val credits: Credits? = null
) {
    val displayTitle: String get() = title ?: name ?: ""
    val date: String? get() = releaseDate ?: firstAirDate
}

data class Genre(val id: Int = 0, val name: String = "")
data class Credits(val cast: List<CastMember>? = null)
data class CastMember(
    val name: String? = null,
    val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null
)

object TmdbImages {
    fun poster(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    fun backdrop(path: String?) = path?.let { "https://image.tmdb.org/t/p/w780$it" }
    fun profile(path: String?) = path?.let { "https://image.tmdb.org/t/p/w185$it" }
}
