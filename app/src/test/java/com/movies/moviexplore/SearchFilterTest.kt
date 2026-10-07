package com.movies.moviexplore

import com.movies.moviexplore.data.model.DiscoverFilter
import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.SortOption
import com.movies.moviexplore.data.model.inSearchOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** While searching, Sort, Region and Language are applied to TMDB's search results by the app. */
class SearchFilterTest {

    private fun DiscoverFilter.accepts(
        type: MediaType = MediaType.MOVIE,
        genres: List<Int> = emptyList(),
        language: String? = "en",
        countries: List<String> = emptyList(),
        votes: Int = 1000,
    ) = acceptsSearchResult(type, genres, language, countries, votes)

    @Test
    fun default_filter_keeps_everything() {
        assertTrue(DiscoverFilter().accepts(votes = 0, language = null))
        assertFalse(DiscoverFilter().filtersSearchLocally)
    }

    @Test
    fun genre_and_language_must_match() {
        assertTrue(DiscoverFilter(genreIds = setOf(35)).accepts(genres = listOf(28, 35)))
        assertFalse(DiscoverFilter(genreIds = setOf(35)).accepts(genres = listOf(28)))
        assertTrue(DiscoverFilter(language = "hi").accepts(language = "hi"))
        assertFalse(DiscoverFilter(language = "hi").accepts(language = "en"))
    }

    @Test
    fun tv_region_uses_origin_country() {
        assertTrue(DiscoverFilter(region = "IN").accepts(type = MediaType.TV, countries = listOf("IN")))
        assertFalse(DiscoverFilter(region = "IN").accepts(type = MediaType.TV, countries = listOf("US")))
    }

    @Test
    fun movie_region_uses_the_regions_languages() {
        // Movie search results have no country, only the original language.
        assertTrue(DiscoverFilter(region = "IN").accepts(language = "ta"))
        assertTrue(DiscoverFilter(region = "KR").accepts(language = "ko"))
        assertFalse(DiscoverFilter(region = "IN").accepts(language = "en"))
    }

    @Test
    fun top_rated_drops_titles_with_too_few_votes_to_rank() {
        assertFalse(DiscoverFilter(sort = SortOption.RATING).accepts(votes = 3))
        assertTrue(DiscoverFilter(sort = SortOption.RATING).accepts(votes = 50))
        assertTrue(DiscoverFilter(sort = SortOption.RATING).filtersSearchLocally)
    }

    @Test
    fun search_results_follow_the_chosen_sort() {
        fun media(id: Int, rating: Double, year: String?) =
            Media(id, MediaType.MOVIE, "t$id", "/p", null, "", year, rating, null)
        val found = listOf(media(1, 6.0, "2010"), media(2, 8.5, "2001"), media(3, 7.0, "2024"), media(4, 5.0, null))

        assertEquals(listOf(1, 2, 3, 4), found.inSearchOrder(SortOption.POPULARITY).map { it.id })
        assertEquals(listOf(2, 3, 1, 4), found.inSearchOrder(SortOption.RATING).map { it.id })
        assertEquals(listOf(3, 1, 2, 4), found.inSearchOrder(SortOption.NEWEST).map { it.id })
    }
}
