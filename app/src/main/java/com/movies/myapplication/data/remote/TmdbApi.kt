package com.movies.myapplication.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** TMDB v3 endpoints. Responses are raw JSON bodies, parsed with org.json in [TmdbJson]. */
interface TmdbApi {

    @GET("trending/{type}/{window}")
    suspend fun trending(
        @Path("type") type: String,
        @Path("window") window: String,
        @Query("page") page: Int = 1,
    ): ResponseBody

    @GET("{type}/popular")
    suspend fun popular(@Path("type") type: String, @Query("page") page: Int = 1): ResponseBody

    @GET("{type}/top_rated")
    suspend fun topRated(@Path("type") type: String, @Query("page") page: Int = 1): ResponseBody

    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @QueryMap params: Map<String, String>): ResponseBody

    @GET("search/{type}")
    suspend fun search(@Path("type") type: String, @QueryMap params: Map<String, String>): ResponseBody

    @GET("genre/{type}/list")
    suspend fun genres(@Path("type") type: String): ResponseBody

    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("append_to_response") append: String? = null,
    ): ResponseBody

    @GET("tv/{id}/season/{season}")
    suspend fun season(@Path("id") tvId: Int, @Path("season") season: Int): ResponseBody

    @GET("tv/{id}/season/{season}/episode/{episode}")
    suspend fun episode(
        @Path("id") tvId: Int,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Query("append_to_response") append: String = "videos",
    ): ResponseBody

    @GET("person/{id}")
    suspend fun person(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "combined_credits",
    ): ResponseBody

    @GET("watch/providers/{type}")
    suspend fun providers(@Path("type") type: String, @Query("watch_region") region: String): ResponseBody

    /** Where one title can be watched, for every country at once. */
    @GET("{type}/{id}/watch/providers")
    suspend fun watchProviders(@Path("type") type: String, @Path("id") id: Int): ResponseBody

    /** Every country TMDB has watch-provider data for. */
    @GET("watch/providers/regions")
    suspend fun watchRegions(): ResponseBody
}
