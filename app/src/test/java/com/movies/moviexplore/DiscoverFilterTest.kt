package com.movies.moviexplore

import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The Browse filter panel's choices must turn into the right TMDB discover parameters. */
class DiscoverFilterTest {

    private fun params(filter: DiscoverFilter, type: MediaType = MediaType.MOVIE) = filter.toDiscoverParams(type, page = 1)

    @Test
    fun default_filter_sorts_by_popularity_only() {
        val p = params(DiscoverFilter())
        assertEquals("popularity.desc", p["sort_by"])
        assertFalse(p.containsKey("with_genres"))
        assertFalse(p.containsKey("with_origin_country"))
        assertFalse(p.containsKey("with_original_language"))
        assertFalse(p.containsKey("with_watch_providers"))
    }

    @Test
    fun year_uses_the_right_field_per_type() {
        assertEquals("2024", params(DiscoverFilter(year = 2024), MediaType.MOVIE)["primary_release_year"])
        assertEquals("2024", params(DiscoverFilter(year = 2024), MediaType.TV)["first_air_date_year"])
    }

    @Test
    fun genre_region_and_language_are_sent() {
        val p = params(DiscoverFilter(genreIds = setOf(35), region = "IN", language = "hi"))
        assertEquals("35", p["with_genres"])
        assertEquals("IN", p["with_origin_country"])
        assertEquals("hi", p["with_original_language"])
    }

    @Test
    fun mood_genres_match_any_panel_genres_match_all() {
        assertEquals("16|10751", params(DiscoverFilter(genreIds = linkedSetOf(16, 10751), matchAnyGenre = true))["with_genres"])
        assertEquals("16,10751", params(DiscoverFilter(genreIds = linkedSetOf(16, 10751)))["with_genres"])
    }

    @Test
    fun top_rated_keeps_the_high_vote_floor_globally() {
        val p = params(DiscoverFilter(sort = SortOption.RATING))
        assertEquals("vote_average.desc", p["sort_by"])
        assertEquals("300", p["vote_count.gte"])
    }

    @Test
    fun top_rated_lowers_the_vote_floor_for_a_region_or_language() {
        // With 300, TMDB returned no Top Rated Indian TV shows and no Tamil movies or shows at all.
        assertEquals("20", params(DiscoverFilter(sort = SortOption.RATING, region = "IN"), MediaType.TV)["vote_count.gte"])
        assertEquals("20", params(DiscoverFilter(sort = SortOption.RATING, language = "ta"))["vote_count.gte"])
    }

    @Test
    fun newest_sorts_by_release_date_up_to_today() {
        val movie = params(DiscoverFilter(sort = SortOption.NEWEST), MediaType.MOVIE)
        assertEquals("primary_release_date.desc", movie["sort_by"])
        assertEquals(java.time.LocalDate.now().toString(), movie["primary_release_date.lte"])
        assertEquals("first_air_date.desc", params(DiscoverFilter(sort = SortOption.NEWEST), MediaType.TV)["sort_by"])
    }

    @Test
    fun channel_needs_a_watch_region_defaulting_to_us() {
        assertEquals("US", params(DiscoverFilter(providerId = 8))["watch_region"])
        val inRegion = params(DiscoverFilter(providerId = 8, region = "IN"))
        assertEquals("8", inRegion["with_watch_providers"])
        assertEquals("IN", inRegion["watch_region"])
    }
}
