package your.app.network

import your.app.network.model.MediaDetails
import your.app.network.model.PagedResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** mediaType is "movie" or "tv". Paths are relative to the TMDB base URL. */
interface TmdbApi {

    @GET("trending/{media}/{window}")
    suspend fun trending(
        @Path("media") mediaType: String,
        @Path("window") window: String,            // "day" or "week"
        @Header("Cache-Control") cacheControl: String? = null
    ): PagedResponse

    /** list: popular, top_rated, now_playing, upcoming, airing_today, on_the_air */
    @GET("{media}/{list}")
    suspend fun list(
        @Path("media") mediaType: String,
        @Path("list") list: String,
        @Query("page") page: Int = 1,
        @Header("Cache-Control") cacheControl: String? = null
    ): PagedResponse

    /** One genre's titles, most popular first (category list screen). */
    @GET("discover/{media}")
    suspend fun discover(
        @Path("media") mediaType: String,
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Header("Cache-Control") cacheControl: String? = null
    ): PagedResponse

    @GET("{media}/{id}/recommendations")
    suspend fun recommendations(
        @Path("media") mediaType: String,
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null
    ): PagedResponse

    @GET("{media}/{id}")
    suspend fun details(
        @Path("media") mediaType: String,
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits",
        @Header("Cache-Control") cacheControl: String? = null
    ): MediaDetails
}
