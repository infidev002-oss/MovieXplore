package com.movies.moviexplore

import com.movies.moviexplore.data.model.FilterOptions
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.Mood
import com.movies.moviexplore.data.model.toFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodFilterTest {

    @Test
    fun happy_mood_filter_contains_comedy_genre_for_movie_and_tv() {
        val filterMovie = setOf(Mood.HAPPY).toFilter(MediaType.MOVIE)
        assertTrue(filterMovie.genreIds.contains(35))
        assertTrue(filterMovie.matchAnyGenre)

        val filterTv = setOf(Mood.HAPPY).toFilter(MediaType.TV)
        assertTrue(filterTv.genreIds.contains(35))
        assertTrue(filterTv.matchAnyGenre)
    }

    @Test
    fun scary_mood_uses_genre_for_movie_and_keyword_for_tv() {
        val filterMovie = setOf(Mood.SCARY).toFilter(MediaType.MOVIE)
        assertTrue(filterMovie.genreIds.contains(27))
        assertTrue(filterMovie.keywordIds.isEmpty())

        val filterTv = setOf(Mood.SCARY).toFilter(MediaType.TV)
        assertTrue(filterTv.genreIds.isEmpty())
        assertTrue(filterTv.keywordIds.contains(315058))
    }

    @Test
    fun combining_multiple_moods_unions_their_genres_and_keywords() {
        val filter = setOf(Mood.HAPPY, Mood.EXCITED).toFilter(MediaType.MOVIE)
        assertTrue(filter.genreIds.contains(35))
        assertTrue(filter.genreIds.contains(28))
        assertTrue(filter.matchAnyGenre)
    }

    @Test
    fun empty_mood_set_creates_empty_filter() {
        val filter = emptySet<Mood>().toFilter(MediaType.MOVIE)
        assertTrue(filter.genreIds.isEmpty())
        assertTrue(filter.keywordIds.isEmpty())
        assertTrue(filter.matchAnyGenre)
    }

    @Test
    fun filterOptions_regionLanguages() {
        assertEquals(setOf("en"), FilterOptions.regionLanguages("US"))
        assertEquals(setOf("en"), FilterOptions.regionLanguages("GB"))
        assertTrue(FilterOptions.regionLanguages("IN").contains("hi"))
        assertTrue(FilterOptions.regionLanguages("IN").contains("ta"))
        assertEquals(setOf("ja"), FilterOptions.regionLanguages("JP"))
        assertEquals(setOf("ko"), FilterOptions.regionLanguages("KR"))
        assertEquals(emptySet<String>(), FilterOptions.regionLanguages("UNKNOWN"))
    }

    @Test
    fun filterOptions_region_and_language_names() {
        assertNotNull(FilterOptions.regionName("US"))
        assertNotNull(FilterOptions.regionName("IN"))
        assertNotNull(FilterOptions.languageName("en"))
        assertNotNull(FilterOptions.languageName("hi"))
        assertEquals(null, FilterOptions.regionName(null))
        assertEquals(null, FilterOptions.languageName(null))
    }
}
