package com.movies.moviexplore.ui.common

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Runs [block] and converts failures into a [Result], keeping coroutine cancellation intact. */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.toUserMessage(): String = when (this) {
    // Checked before IOException, its parent: the network is up, the server just didn't answer in time.
    is SocketTimeoutException -> "The server is taking too long to respond. Please try again."
    is IOException -> "No internet connection. Check your network and try again."
    is HttpException -> when (code()) {
        401 -> "Invalid TMDB API key."
        404 -> "This title could not be found."
        429 -> "Too many requests. Please wait a moment."
        else -> "Server error (${code()}). Please try again."
    }
    else -> "Something went wrong. Please try again."
}

fun formatRuntime(minutes: Int?): String? = when {
    minutes == null || minutes <= 0 -> null
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(isoDate: String?): String? =
    isoDate?.let { runCatching { LocalDate.parse(it).format(dateFormatter) }.getOrNull() }

fun formatRating(value: Double): String = String.format(Locale.US, "%.1f", value)
