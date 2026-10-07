package com.movies.moviexplore

import com.movies.moviexplore.data.model.Country
import com.movies.moviexplore.data.model.Episode
import com.movies.moviexplore.data.model.Media
import com.movies.moviexplore.data.model.MediaDetails
import com.movies.moviexplore.data.model.MediaType
import com.movies.moviexplore.data.model.PageResult
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test
    fun country_flag_converts_two_letter_code_to_regional_indicator_emoji() {
        val us = Country("US", "United States")
        // U = 0x1F1FA, S = 0x1F1F8
        assertEquals(String(Character.toChars(0x1F1FA)) + String(Character.toChars(0x1F1F8)), us.flag)

        val `in` = Country("IN", "India")
        assertEquals(String(Character.toChars(0x1F1EE)) + String(Character.toChars(0x1F1F3)), `in`.flag)

        val invalid = Country("xyz", "Unknown")
        assertEquals("🏳️", invalid.flag)

        val empty = Country("", "Empty")
        assertEquals("🏳️", empty.flag)
    }

    @Test
    fun media_key_combines_type_and_id() {
        val movie = Media(123, MediaType.MOVIE, "Movie", null, null, "", "2024", 8.0, null)
        assertEquals("movie_123", movie.key)

        val tv = Media(456, MediaType.TV, "TV Show", null, null, "", "2023", 7.5, null)
        assertEquals("tv_456", tv.key)
    }

    @Test
    fun video_url_and_thumbnail() {
        val video = Video("abc123xyz", "Official Trailer", "Trailer")
        assertEquals("https://www.youtube.com/watch?v=abc123xyz", video.url)
        assertEquals("https://img.youtube.com/vi/abc123xyz/hqdefault.jpg", video.thumbnailUrl)
    }

    @Test
    fun mediaDetails_watchUrl() {
        val media = Media(999, MediaType.MOVIE, "Test", null, null, "", "2024", 8.0, null)
        val details = MediaDetails(media, emptyList(), null, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("https://www.themoviedb.org/movie/999/watch", details.watchUrl)
    }

    @Test
    fun episode_stillUrl_prefers_still_and_falls_back_to_show_backdrop() {
        val epWithStill = Episode(1, 1, "Pilot", "/ep_still.jpg", 45, "2024-01-01")
        assertEquals("https://image.tmdb.org/t/p/w300/ep_still.jpg", epWithStill.stillUrl("/show_backdrop.jpg"))

        val epWithoutStill = Episode(2, 2, "Episode 2", null, 45, "2024-01-08")
        assertEquals("https://image.tmdb.org/t/p/w780/show_backdrop.jpg", epWithoutStill.stillUrl("/show_backdrop.jpg"))

        val epNoImages = Episode(3, 3, "Episode 3", null, 45, "2024-01-15")
        assertNull(epNoImages.stillUrl(null))
    }

    @Test
    fun tmdbImage_paths() {
        assertNull(TmdbImage.poster(null))
        assertNull(TmdbImage.posterLarge(null))
        assertNull(TmdbImage.backdrop(null))
        assertNull(TmdbImage.profile(null))
        assertNull(TmdbImage.logo(null))
        assertNull(TmdbImage.still(null))
        assertNull(TmdbImage.stillLarge(null))

        assertEquals("https://image.tmdb.org/t/p/w342/test.jpg", TmdbImage.poster("/test.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/test.jpg", TmdbImage.posterLarge("/test.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/test.jpg", TmdbImage.backdrop("/test.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w185/test.jpg", TmdbImage.profile("/test.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w154/test.jpg", TmdbImage.logo("/test.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w300/test.jpg", TmdbImage.still("/test.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/test.jpg", TmdbImage.stillLarge("/test.jpg"))
    }

    @Test
    fun pageResult_hasMore() {
        assertTrue(PageResult(emptyList(), page = 1, totalPages = 5).hasMore)
        assertFalse(PageResult(emptyList(), page = 5, totalPages = 5).hasMore)
        assertFalse(PageResult(emptyList(), page = 6, totalPages = 5).hasMore)
    }
}
