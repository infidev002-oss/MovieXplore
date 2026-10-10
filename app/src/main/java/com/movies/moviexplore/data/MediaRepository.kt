package com.movies.moviexplore.data

import android.util.LruCache
import com.movies.moviexplore.data.model.Country
import com.movies.moviexplore.data.model.Credit
import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.EpisodeDetails
import com.movies.moviexplore.data.model.Genre
import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.MediaDetails
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.OfferType
import com.movies.moviexplore.data.model.PageResult
import com.movies.moviexplore.data.model.Person
import com.movies.moviexplore.data.model.Provider
import com.movies.moviexplore.data.model.SeasonInfo
import com.movies.moviexplore.data.model.Video
import com.movies.moviexplore.data.model.WatchAvailability
import com.movies.moviexplore.data.model.WatchOffer
import com.movies.moviexplore.network.NetworkClient
import com.movies.moviexplore.network.TmdbApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

/**
 * The genre a poster card names. TMDB lists a title's genres in its own order, so in a list filtered by
 * genre (e.g. "Action") the first one is often another genre ("Science Fiction"), which reads as a wrong
 * result. Prefer a genre from [shown] that the title has; otherwise its first known genre.
 */
internal fun cardGenre(genreIds: List<Int>, shown: Set<Int>, names: Map<Int, String>): String? =
    (genreIds.filter { it in shown } + genreIds).firstNotNullOfOrNull { names[it] }

/** TMDB content. Every call runs on the IO dispatcher, including JSON parsing. */
class MediaRepository(private val api: TmdbApi) {

    companion object {
        /** The app's one repository, on the kit's shared TMDB client. Created on first use, never in Application. */
        val shared: MediaRepository by lazy { MediaRepository(NetworkClient.tmdbApi) }
    }

    private val genreCache = mutableMapOf<MediaType, List<Genre>>()
    private val genreMutex = Mutex()
    private var countriesCache: List<Country>? = null
    private val countriesMutex = Mutex()
    // In memory only, for this app session: titles are in the content language, so [clearLanguageCache] empties them.
    private val seasonCache = LruCache<Int, List<SeasonInfo>>(30)
    private val episodeCache = LruCache<String, List<Episode>>(60)

    suspend fun genres(type: MediaType): List<Genre> = io {
        genreMutex.withLock {
            genreCache[type] ?: api.genres(type.path).json().array("genres")
                .objects()
                .map { Genre(it.getInt("id"), it.optString("name")) }
                .also { genreCache[type] = it }
        }
    }

    /** Forgets genre names, seasons and episodes, which TMDB returns in the content language, after the user picks another one. */
    suspend fun clearLanguageCache() {
        genreMutex.withLock { genreCache.clear() }
        seasonCache.evictAll()
        episodeCache.evictAll()
    }

    suspend fun trending(type: MediaType?, page: Int = 1): PageResult = io {
        api.trending(type?.path ?: "all", if (type == null) "day" else "week", page).json().toPage(type)
    }

    suspend fun popular(type: MediaType, page: Int = 1): PageResult = io {
        api.popular(type.path, page).json().toPage(type)
    }

    suspend fun topRated(type: MediaType, page: Int = 1): PageResult = io {
        api.topRated(type.path, page).json().toPage(type)
    }

    suspend fun discover(type: MediaType, filter: DiscoverFilter, page: Int): PageResult = io {
        api.discover(type.path, filter.toDiscoverParams(type, page)).json().toPage(type, filter.genreIds)
    }

    /** TMDB search only supports a year filter, so the rest is applied to the results locally ([DiscoverFilter.acceptsSearchResult]). */
    suspend fun search(type: MediaType, query: String, filter: DiscoverFilter, page: Int): PageResult = io {
        val params = buildMap {
            put("query", query)
            put("page", page.toString())
            put("include_adult", "false")
            filter.year?.let { put(if (type == MediaType.MOVIE) "primary_release_year" else "first_air_date_year", it.toString()) }
        }
        val json = api.search(type.path, params).json()
        val kept = json.array("results").objects().filter { item ->
            filter.acceptsSearchResult(
                type = type,
                genreIds = item.array("genre_ids").ints(),
                originalLanguage = item.str("original_language"),
                originCountries = item.array("origin_country").strings(),
                voteCount = item.optInt("vote_count"),
            )
        }
        PageResult(kept.toMedia(type, filter.genreIds), json.optInt("page", 1), json.optInt("total_pages", 1))
    }

    suspend fun providers(type: MediaType, region: String): List<Provider> = io {
        api.providers(type.path, region).json().array("results").objects()
            .sortedBy { it.optInt("display_priority", Int.MAX_VALUE) }
            .take(25)
            .map { Provider(it.getInt("provider_id"), it.optString("provider_name")) }
    }

    /** Where a title can be watched, keyed by country code. */
    suspend fun watchAvailability(type: MediaType, id: Int): Map<String, WatchAvailability> = io {
        val results = api.watchProviders(type.path, id).json().optJSONObject("results") ?: JSONObject()
        results.keys().asSequence().associateWith { code ->
            val country = results.getJSONObject(code)
            val offers = listOf(
                OfferType.STREAM to "flatrate",
                OfferType.FREE to "free",
                OfferType.ADS to "ads",
                OfferType.RENT to "rent",
                OfferType.BUY to "buy",
            ).flatMap { (offerType, field) ->
                country.array(field).objects()
                    .sortedBy { it.optInt("display_priority", Int.MAX_VALUE) }
                    .map { WatchOffer(it.getInt("provider_id"), it.optString("provider_name"), it.str("logo_path"), offerType) }
            }
            WatchAvailability(country.str("link"), offers)
        }
    }

    /** Every country with watch data, named in the device language and sorted by name. Cached after the first call. */
    suspend fun countries(): List<Country> = io {
        countriesMutex.withLock {
            countriesCache ?: api.watchRegions().json().array("results").objects()
                .map { item ->
                    val code = item.optString("iso_3166_1")
                    val localName = runCatching { Locale.Builder().setRegion(code).build().displayCountry }.getOrNull()
                    // Locale returns the code itself for regions it doesn't know.
                    Country(code, localName?.takeIf { it.isNotBlank() && it != code } ?: item.optString("english_name").ifBlank { code })
                }
                .sortedWith(compareBy(Collator.getInstance()) { it.name })
                .also { countriesCache = it }
        }
    }

    suspend fun details(type: MediaType, id: Int): MediaDetails = io {
        val dto = api.details(type.path, id, append = "videos,credits,similar").json()
        val genreNames = dto.array("genres").objects().map { it.optString("name") }
        val media = Media(
            id = dto.getInt("id"),
            type = type,
            title = dto.str("title") ?: dto.str("name").orEmpty(),
            posterPath = dto.str("poster_path"),
            backdropPath = dto.str("backdrop_path"),
            overview = dto.str("overview").orEmpty(),
            year = (dto.str("release_date") ?: dto.str("first_air_date"))?.take(4)?.takeIf { it.isNotBlank() },
            rating = dto.optDouble("vote_average", 0.0),
            genre = genreNames.firstOrNull(),
        )
        val credits = dto.optJSONObject("credits")
        val creators = dto.array("created_by").objects().map { Credit(it.getInt("id"), it.optString("name"), "Creator", it.str("profile_path")) } +
            credits?.array("crew")?.objects().orEmpty().keyCrew("Director")
        val cast = credits?.array("cast")?.objects().orEmpty().take(20).map { it.asCast() }
        val seasons = dto.array("seasons").objects().map { it.asSeason() }.let { all -> all.filter { it.number > 0 }.ifEmpty { all } }
        // The season screen reuses this list instead of downloading the show again.
        if (type == MediaType.TV) seasonCache.put(id, seasons)
        MediaDetails(
            media = media,
            genres = genreNames,
            country = dto.array("origin_country").strings().firstOrNull()
                ?: dto.array("production_countries").objects().firstOrNull()?.str("iso_3166_1"),
            videos = dto.optJSONObject("videos")?.array("results")?.objects().orEmpty().toTrailers(),
            credits = (cast + creators).distinctBy { it.id },
            seasons = seasons,
            similar = dto.optJSONObject("similar")?.array("results")?.objects().orEmpty().toMedia(type),
        )
    }

    /** A show's seasons; downloaded once, or already known from [details]. */
    suspend fun seasons(tvId: Int): List<SeasonInfo> = seasonCache.get(tvId) ?: io {
        val seasons = api.details(MediaType.TV.path, tvId).json().array("seasons").objects().map { it.asSeason() }
        seasons.filter { it.number > 0 }.ifEmpty { seasons }.also { seasonCache.put(tvId, it) }
    }

    /** A season's episodes, downloaded once per season; switching back to a season shows them from memory. */
    suspend fun episodes(tvId: Int, season: Int): List<Episode> = cachedEpisodes(tvId, season) ?: io {
        api.season(tvId, season).json().array("episodes").objects().map {
            Episode(
                id = it.getInt("id"),
                number = it.optInt("episode_number"),
                name = it.optString("name"),
                stillPath = it.str("still_path"),
                runtime = it.intOrNull("runtime"),
                airDate = it.str("air_date"),
            )
        }.also { episodeCache.put(episodeKey(tvId, season), it) }
    }

    /** Episodes already downloaded for this season, or null; lets a screen show them without a loading state. */
    fun cachedEpisodes(tvId: Int, season: Int): List<Episode>? = episodeCache.get(episodeKey(tvId, season))

    private fun episodeKey(tvId: Int, season: Int) = "${tvId}_$season"

    suspend fun episode(tvId: Int, season: Int, episode: Int): EpisodeDetails = io {
        val dto = api.episode(tvId, season, episode).json()
        EpisodeDetails(
            season = dto.optInt("season_number"),
            number = dto.optInt("episode_number"),
            name = dto.optString("name"),
            overview = dto.str("overview").orEmpty(),
            stillPath = dto.str("still_path"),
            airDate = dto.str("air_date"),
            runtime = dto.intOrNull("runtime"),
            rating = dto.optDouble("vote_average", 0.0),
            credits = (dto.array("crew").objects().keyCrew("Director", "Writer") + dto.array("guest_stars").objects().map { it.asCast() })
                .distinctBy { it.id },
            videos = dto.optJSONObject("videos")?.array("results")?.objects().orEmpty().toTrailers(),
        )
    }

    suspend fun person(id: Int): Person = io {
        val dto = api.person(id).json()
        val credits = dto.optJSONObject("combined_credits")?.array("cast")?.objects().orEmpty()
            .distinctBy { it.str("media_type") to it.getInt("id") }
            .sortedByDescending { it.optDouble("popularity", 0.0) }
        Person(
            id = dto.getInt("id"),
            name = dto.optString("name"),
            biography = dto.str("biography").orEmpty(),
            birthday = dto.str("birthday"),
            placeOfBirth = dto.str("place_of_birth"),
            profilePath = dto.str("profile_path"),
            movies = credits.filter { it.str("media_type") == MediaType.MOVIE.path }.toMedia(MediaType.MOVIE),
            tvShows = credits.filter { it.str("media_type") == MediaType.TV.path }.toMedia(MediaType.TV),
        )
    }

    private suspend fun JSONObject.toPage(type: MediaType?, shownGenres: Set<Int> = emptySet()) =
        PageResult(array("results").objects().toMedia(type, shownGenres), optInt("page", 1), optInt("total_pages", 1))

    /**
     * [fallbackType] is used when the response does not tag each item with a media type. Titles without a
     * poster are dropped: TMDB lists (especially "similar" and deep search pages) include stub entries with
     * no artwork, which would show as empty cards. [shownGenres] are the genres the list is filtered by;
     * each card names one of those (see [cardGenre]).
     */
    private suspend fun List<JSONObject>.toMedia(fallbackType: MediaType?, shownGenres: Set<Int> = emptySet()): List<Media> {
        val genreNames = mutableMapOf<MediaType, Map<Int, String>>()
        return mapNotNull { dto ->
            val posterPath = dto.str("poster_path") ?: return@mapNotNull null
            val type = when (dto.str("media_type")) {
                MediaType.MOVIE.path -> MediaType.MOVIE
                MediaType.TV.path -> MediaType.TV
                null -> fallbackType
                else -> null
            } ?: return@mapNotNull null
            val names = genreNames.getOrPut(type) {
                // Genre names are cosmetic; a failed lookup must not fail the whole list.
                runCatching { genres(type) }.getOrDefault(emptyList()).associate { it.id to it.name }
            }
            Media(
                id = dto.getInt("id"),
                type = type,
                title = dto.str("title") ?: dto.str("name").orEmpty(),
                posterPath = posterPath,
                backdropPath = dto.str("backdrop_path"),
                overview = dto.str("overview").orEmpty(),
                year = (dto.str("release_date") ?: dto.str("first_air_date"))?.take(4)?.takeIf { it.isNotBlank() },
                rating = dto.optDouble("vote_average", 0.0),
                genre = cardGenre(dto.array("genre_ids").ints(), shownGenres, names),
            )
        }
    }

    private fun JSONObject.asCast() =
        Credit(getInt("id"), optString("name"), if (optInt("gender") == 1) "Actress" else "Actor", str("profile_path"))

    private fun JSONObject.asSeason() = SeasonInfo(optInt("season_number"), optString("name"), optInt("episode_count"))

    private fun List<JSONObject>.keyCrew(vararg jobs: String) =
        filter { it.str("job") in jobs }.map { Credit(it.getInt("id"), it.optString("name"), it.str("job").orEmpty(), it.str("profile_path")) }

    private fun List<JSONObject>.toTrailers() =
        filter { it.str("site") == "YouTube" && (it.str("type") == "Trailer" || it.str("type") == "Teaser") }
            .sortedBy { if (it.str("type") == "Trailer") 0 else 1 }
            .map { Video(it.optString("key"), it.optString("name"), it.optString("type")) }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

// org.json helpers. optString() turns JSON null into the text "null", so nullable fields go through str().

private fun ResponseBody.json(): JSONObject = use { JSONObject(it.string()) }

internal fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else optInt(name)

private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.ints(): List<Int> = (0 until length()).map { optInt(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
