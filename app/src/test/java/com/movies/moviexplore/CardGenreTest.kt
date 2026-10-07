package com.movies.moviexplore

import com.movies.moviexplore.data.cardGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A poster card in a genre-filtered list names the filtered genre, not just the title's first one. */
class CardGenreTest {

    private val names = mapOf(28 to "Action", 12 to "Adventure", 878 to "Science Fiction", 27 to "Horror", 35 to "Comedy")

    @Test
    fun unfiltered_list_shows_the_first_genre() {
        assertEquals("Science Fiction", cardGenre(listOf(878, 28, 12), emptySet(), names))
    }

    @Test
    fun action_list_shows_action_even_when_it_is_not_first() {
        // Spider-Man: Brand New Day is "Science Fiction, Action, Adventure" on TMDB.
        assertEquals("Action", cardGenre(listOf(878, 28, 12), setOf(28), names))
    }

    @Test
    fun several_filtered_genres_use_the_titles_own_order_among_them() {
        assertEquals("Horror", cardGenre(listOf(27, 28, 35), setOf(35, 27), names))
    }

    @Test
    fun title_without_the_filtered_genre_falls_back_to_its_first() {
        assertEquals("Science Fiction", cardGenre(listOf(878, 12), setOf(35), names))
    }

    @Test
    fun unknown_ids_are_skipped_and_no_genres_give_null() {
        assertEquals("Action", cardGenre(listOf(9999, 28), emptySet(), names))
        assertNull(cardGenre(emptyList(), setOf(28), names))
    }
}
