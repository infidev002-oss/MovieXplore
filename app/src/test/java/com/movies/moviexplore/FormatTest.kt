package com.movies.moviexplore

import com.movies.moviexplore.ui.common.formatDate
import com.movies.moviexplore.ui.common.formatRating
import com.movies.moviexplore.ui.common.formatRuntime
import com.movies.moviexplore.ui.common.safeCall
import com.movies.moviexplore.ui.common.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class FormatTest {

    @Test
    fun formatRuntime_null_or_non_positive_returns_null() {
        assertNull(formatRuntime(null))
        assertNull(formatRuntime(0))
        assertNull(formatRuntime(-5))
    }

    @Test
    fun formatRuntime_less_than_one_hour_formats_minutes() {
        assertEquals("45 min", formatRuntime(45))
        assertEquals("1 min", formatRuntime(1))
        assertEquals("59 min", formatRuntime(59))
    }

    @Test
    fun formatRuntime_exact_hours() {
        assertEquals("1 h", formatRuntime(60))
        assertEquals("2 h", formatRuntime(120))
        assertEquals("3 h", formatRuntime(180))
    }

    @Test
    fun formatRuntime_hours_and_minutes() {
        assertEquals("1 h 25 min", formatRuntime(85))
        assertEquals("2 h 22 min", formatRuntime(142))
    }

    @Test
    fun formatRating_formats_one_decimal_place() {
        assertEquals("8.5", formatRating(8.53))
        assertEquals("7.0", formatRating(7.0))
        assertEquals("0.0", formatRating(0.0))
        assertEquals("9.9", formatRating(9.94))
    }

    @Test
    fun formatDate_parses_iso_date() {
        val formatted = formatDate("2024-05-15")
        // Formatted according to Medium localized format (e.g. May 15, 2024 in US locale)
        assertTrue(formatted != null && formatted.contains("2024"))
    }

    @Test
    fun formatDate_returns_null_for_null_or_malformed_string() {
        assertNull(formatDate(null))
        assertNull(formatDate(""))
        assertNull(formatDate("not-a-date"))
        assertNull(formatDate("2024/05/15"))
    }

    @Test
    fun toUserMessage_returns_expected_messages_for_exceptions() {
        val timeoutMsg = SocketTimeoutException("timeout").toUserMessage()
        assertEquals("The server is taking too long to respond. Please try again.", timeoutMsg)

        val ioMsg = IOException("network down").toUserMessage()
        assertEquals("No internet connection. Check your network and try again.", ioMsg)

        fun httpException(code: Int): HttpException {
            val body = "".toResponseBody("text/plain".toMediaType())
            return HttpException(Response.error<String>(code, body))
        }

        assertEquals("Invalid TMDB API key.", httpException(401).toUserMessage())
        assertEquals("This title could not be found.", httpException(404).toUserMessage())
        assertEquals("Too many requests. Please wait a moment.", httpException(429).toUserMessage())
        assertEquals("Server error (500). Please try again.", httpException(500).toUserMessage())

        val genericMsg = IllegalStateException("generic").toUserMessage()
        assertEquals("Something went wrong. Please try again.", genericMsg)
    }

    @Test
    fun safeCall_captures_success_and_exception() = runBlocking {
        val successResult = safeCall { "hello" }
        assertTrue(successResult.isSuccess)
        assertEquals("hello", successResult.getOrNull())

        val failResult = safeCall<String> { throw RuntimeException("boom") }
        assertTrue(failResult.isFailure)
        assertEquals("boom", failResult.exceptionOrNull()?.message)
    }

    @Test(expected = CancellationException::class)
    fun safeCall_rethrows_cancellation_exception(): Unit = runBlocking {
        safeCall<String> { throw CancellationException("cancelled") }
    }
}
