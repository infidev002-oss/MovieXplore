package com.movies.moviexplore.data.model

import com.movies.moviexplore.data.ContentLocale
import java.time.LocalDate
import java.util.Locale

enum class SortOption(val label: String) {
    POPULARITY("Popularity"),
    RATING("Top Rated"),
    NEWEST("Newest"),
}

data class DiscoverFilter(
    val year: Int? = null,
    val genreIds: Set<Int> = emptySet(),
    val matchAnyGenre: Boolean = false,
    val keywordIds: Set<Int> = emptySet(),
    val sort: SortOption = SortOption.POPULARITY,
    val region: String? = null,
    val language: String? = null,
    val providerId: Int? = null,
) {
    fun toDiscoverParams(type: MediaType, page: Int): Map<String, String> = buildMap {
        put("page", page.toString())
        put("include_adult", "false")
        val dateField = if (type == MediaType.MOVIE) "primary_release_date" else "first_air_date"
        when (sort) {
            SortOption.POPULARITY -> put("sort_by", "popularity.desc")
            SortOption.RATING -> {
                put("sort_by", "vote_average.desc")
                // Regional and non-English titles rarely reach 300 votes (e.g. Indian TV or Tamil films had none),
                // so a narrowed list uses a lower floor that still keeps one-vote outliers out.
                val narrowed = region != null || language != null
                put("vote_count.gte", if (narrowed) "20" else "300")
            }
            SortOption.NEWEST -> {
                put("sort_by", "$dateField.desc")
                put("$dateField.lte", LocalDate.now().toString())
                put("vote_count.gte", "10")
            }
        }
        year?.let { put(if (type == MediaType.MOVIE) "primary_release_year" else "first_air_date_year", it.toString()) }
        if (genreIds.isNotEmpty()) put("with_genres", genreIds.joinToString(if (matchAnyGenre) "|" else ","))
        if (keywordIds.isNotEmpty()) put("with_keywords", keywordIds.joinToString("|"))
        region?.let { put("with_origin_country", it) }
        language?.let { put("with_original_language", it) }
        providerId?.let {
            put("with_watch_providers", it.toString())
            put("watch_region", region ?: ContentLocale.country)
        }
    }

    val isDefault: Boolean get() = this == DiscoverFilter()

    /**
     * TMDB's text search only filters by year, so while searching the rest is applied to each result here.
     * Movies in search results carry no country, so for them [region] is matched through the languages
     * made there ([FilterOptions.regionLanguages]); a region with no known languages doesn't filter movies.
     * Top Rated drops titles with too few votes to rank. Channels can't be checked from search results.
     */
    fun acceptsSearchResult(
        type: MediaType,
        genreIds: List<Int>,
        originalLanguage: String?,
        originCountries: List<String>,
        voteCount: Int,
    ): Boolean {
        if (this.genreIds.isNotEmpty() && genreIds.none { it in this.genreIds }) return false
        if (language != null && originalLanguage != language) return false
        if (region != null) {
            val inRegion = if (type == MediaType.TV) {
                region in originCountries
            } else {
                FilterOptions.regionLanguages(region).let { it.isEmpty() || originalLanguage in it }
            }
            if (!inRegion) return false
        }
        if (sort == SortOption.RATING && voteCount < SEARCH_RATING_MIN_VOTES) return false
        return true
    }

    /** Whether search results are filtered locally, so a page may come back short (or empty). */
    val filtersSearchLocally: Boolean
        get() = genreIds.isNotEmpty() || language != null || region != null || sort == SortOption.RATING

    companion object {
        const val DEFAULT_WATCH_REGION = "US"
        private const val SEARCH_RATING_MIN_VOTES = 20
    }
}

/**
 * Search results in the filter's sort order. Popularity keeps TMDB's relevance order; Top Rated and Newest
 * reorder everything loaded so far (stable, so equal titles keep their relevance order).
 */
fun List<Media>.inSearchOrder(sort: SortOption): List<Media> = when (sort) {
    SortOption.POPULARITY -> this
    SortOption.RATING -> sortedByDescending { it.rating }
    SortOption.NEWEST -> sortedByDescending { it.year?.toIntOrNull() ?: Int.MIN_VALUE }
}

object FilterOptions {
    private val regionCodes = listOf("US", "IN", "GB", "CA", "AU", "DE", "FR", "ES", "IT", "JP", "KR", "CN", "BR", "MX")
    private val languageCodes = listOf("en", "hi", "ta", "te", "ml", "kn", "bn", "mr", "ja", "ko", "zh", "es", "fr", "de", "it", "pt", "ru", "tr")

    val years: List<Int> get() = (LocalDate.now().year + 1 downTo 1950).toList()

    val regions: List<Pair<String, String>> by lazy {
        regionCodes.map { it to Locale.Builder().setRegion(it).build().displayCountry }
    }

    val languages: List<Pair<String, String>> by lazy {
        languageCodes.map { it to Locale.forLanguageTag(it).displayLanguage }
    }

    fun regionName(code: String?) = regions.firstOrNull { it.first == code }?.second
    fun languageName(code: String?) = languages.firstOrNull { it.first == code }?.second

    /** The main languages films from a region are made in; used to match movie search results to a region. */
    fun regionLanguages(code: String): Set<String> = when (code) {
        "US", "GB", "CA", "AU" -> setOf("en")
        "IN" -> setOf("hi", "ta", "te", "ml", "kn", "bn", "mr", "pa", "gu")
        "DE" -> setOf("de")
        "FR" -> setOf("fr")
        "ES", "MX" -> setOf("es")
        "IT" -> setOf("it")
        "JP" -> setOf("ja")
        "KR" -> setOf("ko")
        "CN" -> setOf("zh", "cn")
        "BR" -> setOf("pt")
        else -> emptySet()
    }
}

/** TMDB genres and keywords that express a mood for one media type. */
class MoodQuery(val genreIds: Set<Int> = emptySet(), val keywordIds: Set<Int> = emptySet())

private fun genres(vararg ids: Int) = MoodQuery(genreIds = ids.toSet())
private fun keywords(vararg ids: Int) = MoodQuery(keywordIds = ids.toSet())

/**
 * Maps a viewer's mood to TMDB discover queries. Movies and TV use different genre lists
 * (TV has no Horror, Romance, Music or History genre), so TV falls back to keywords there.
 */
/** [hint] says in a few words what the mood finds, matching its genres and keywords. */
enum class Mood(val emoji: String, val label: String, val hint: String, val movie: MoodQuery, val tv: MoodQuery) {
    HAPPY("😊", "Happy", "Feel-good comedies", genres(35), genres(35)),
    BIOGRAPHY("💡", "Biography", "Real lives, true stories", genres(36), keywords(5565)),
    CHILL("🧘", "Chill", "Easy animated fun", genres(16, 10751), genres(16, 10751)),
    SAD("😢", "Sad", "Moving dramas", genres(18), genres(18)),
    ROMANTIC("❤️", "Romantic", "Love stories", genres(10749), keywords(9840)),
    EXCITED("🔥", "Excited", "Non-stop action", genres(28), genres(10759)),
    CURIOUS("🤔", "Curious", "Mysteries to solve", genres(9648), genres(9648)),
    SCARY("👻", "Scary", "Horror and chills", genres(27), keywords(315058)),
    EPIC("💥", "Epic", "Fantasy and war sagas", genres(14, 10752), genres(10765, 10768)),
    FAMILY("👨‍👩‍👧", "Family Time", "Watch all together", genres(10751), genres(10751)),
    MUSICAL("🎵", "Musical Vibes", "Stories led by music", genres(10402), keywords(4344)),
    THOUGHTFUL("🧠", "Thought-Provoking", "Mind-bending sci-fi", genres(878), genres(10765)),
    ADVENTUROUS("🏔️", "Adventurous", "Journeys and quests", genres(12), genres(10759)),
    ZEN("🌿", "Zen", "Calm documentaries", genres(99), genres(99)),
    SUPERHERO("🦸", "Superhero", "Heroes with powers", keywords(9715), keywords(9715)),
    REAL_LIFE("🌍", "Real Life", "Docs and reality", genres(99, 36), genres(99, 10764)),
}

/** Combines the selected moods into one discover filter for [type]: any matching genre, any matching keyword. */
fun Set<Mood>.toFilter(type: MediaType): DiscoverFilter {
    val queries = map { if (type == MediaType.MOVIE) it.movie else it.tv }
    return DiscoverFilter(
        genreIds = queries.flatMapTo(HashSet()) { it.genreIds },
        keywordIds = queries.flatMapTo(HashSet()) { it.keywordIds },
        matchAnyGenre = true,
    )
}
