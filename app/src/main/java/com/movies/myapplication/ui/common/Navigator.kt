package com.movies.myapplication.ui.common

import android.content.Context
import android.content.Intent
import com.movies.myapplication.data.model.Genre
import com.movies.myapplication.data.model.MediaType
import com.movies.myapplication.data.model.Mood
import com.movies.myapplication.ui.browse.BrowseActivity
import com.movies.myapplication.ui.category.CategoryActivity
import com.movies.myapplication.ui.details.DetailsActivity
import com.movies.myapplication.ui.details.EpisodeActivity
import com.movies.myapplication.ui.details.SeasonActivity
import com.movies.myapplication.ui.mood.MoodResultsActivity
import com.movies.myapplication.ui.person.PersonActivity

/** Which TMDB list a category screen shows. */
enum class CategoryList { GENRE, TRENDING, POPULAR, TOP_RATED }

/** A titled poster grid for one category; [genreId] applies only to [CategoryList.GENRE]. */
data class CategoryRequest(val type: MediaType, val title: String, val list: CategoryList, val genreId: Int = 0) {
    companion object {
        fun genre(type: MediaType, genre: Genre) = CategoryRequest(type, genre.name, CategoryList.GENRE, genre.id)
    }
}

/** Opens screens. Every screen is an Activity, and its arguments travel as Intent extras. */
object Navigator {
    const val EXTRA_ID = "id"
    const val EXTRA_TYPE = "type"
    const val EXTRA_TITLE = "title"
    const val EXTRA_LIST = "list"
    const val EXTRA_GENRE = "genre"
    const val EXTRA_MOODS = "moods"
    const val EXTRA_SEASON = "season"
    const val EXTRA_EPISODE = "episode"
    const val EXTRA_BACKDROP = "backdrop"

    fun details(context: Context, id: Int, type: MediaType) = context.open<DetailsActivity> {
        putExtra(EXTRA_ID, id)
        putExtra(EXTRA_TYPE, type.name)
    }

    fun browse(context: Context, type: MediaType, title: String) = context.open<BrowseActivity> {
        putExtra(EXTRA_TYPE, type.name)
        putExtra(EXTRA_TITLE, title)
    }

    fun category(context: Context, request: CategoryRequest) = context.open<CategoryActivity> {
        putExtra(EXTRA_TYPE, request.type.name)
        putExtra(EXTRA_TITLE, request.title)
        putExtra(EXTRA_LIST, request.list.name)
        putExtra(EXTRA_GENRE, request.genreId)
    }

    fun moodResults(context: Context, moods: Collection<Mood>) = context.open<MoodResultsActivity> {
        putExtra(EXTRA_MOODS, moods.joinToString(",") { it.name })
    }

    fun season(context: Context, tvId: Int, showName: String, season: Int, backdropPath: String?) = context.open<SeasonActivity> {
        putExtra(EXTRA_ID, tvId)
        putExtra(EXTRA_TITLE, showName)
        putExtra(EXTRA_SEASON, season)
        putExtra(EXTRA_BACKDROP, backdropPath)
    }

    fun episode(context: Context, tvId: Int, showName: String, season: Int, episode: Int, backdropPath: String?) =
        context.open<EpisodeActivity> {
            putExtra(EXTRA_ID, tvId)
            putExtra(EXTRA_TITLE, showName)
            putExtra(EXTRA_SEASON, season)
            putExtra(EXTRA_EPISODE, episode)
            putExtra(EXTRA_BACKDROP, backdropPath)
        }

    fun person(context: Context, id: Int) = context.open<PersonActivity> { putExtra(EXTRA_ID, id) }

    private inline fun <reified T> Context.open(extras: Intent.() -> Unit) {
        startActivity(Intent(this, T::class.java).apply(extras))
    }
}

/** Reads a [MediaType] extra written by [Navigator]. */
fun Intent.mediaType(): MediaType = MediaType.valueOf(getStringExtra(Navigator.EXTRA_TYPE) ?: MediaType.MOVIE.name)
