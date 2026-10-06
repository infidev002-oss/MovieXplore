package com.movies.myapplication.ui.person

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.movies.myapplication.R
import com.movies.myapplication.appContainer
import com.movies.myapplication.data.model.Media
import com.movies.myapplication.data.model.Person
import com.movies.myapplication.data.model.TmdbImage
import com.movies.myapplication.ui.common.BaseActivity
import com.movies.myapplication.ui.common.ImageLoader
import com.movies.myapplication.ui.common.Navigator
import com.movies.myapplication.ui.common.addMediaRow
import com.movies.myapplication.ui.common.makeExpandable
import com.movies.myapplication.ui.common.padForSystemBars
import com.movies.myapplication.ui.common.placeBelowStatusBar
import com.movies.myapplication.ui.common.renderState
import com.movies.myapplication.ui.common.show

/** An actor or crew member: photo, birth details, biography, and their movies and TV shows. */
class PersonActivity : BaseActivity() {

    private var rendered: Person? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person)
        val store = PersonStore(appContainer.mediaRepository, intent.getIntExtra(Navigator.EXTRA_ID, 0)).attach()

        findViewById<View>(R.id.floating_back).apply {
            placeBelowStatusBar()
            setOnClickListener { finish() }
        }
        val scroll = findViewById<ScrollView>(R.id.person_scroll)
        scroll.padForSystemBars(top = true, bottom = true)
        findViewById<View>(R.id.person_photo_frame).clipToOutline = true

        store.state.observe { state ->
            val person = state.person
            findViewById<View>(R.id.person_state).renderState(
                loading = person == null && state.isLoading,
                message = state.error.takeIf { person == null },
                onRetry = { store.onIntent(PersonIntent.Retry) },
            )
            scroll.show(person != null)
            if (person != null && person != rendered) {
                rendered = person
                bind(person)
            }
        }
    }

    private fun bind(person: Person) {
        ImageLoader.load(findViewById(R.id.person_photo), TmdbImage.posterLarge(person.profilePath))
        findViewById<TextView>(R.id.person_name).text = person.name
        findViewById<View>(R.id.person_born_label).show(person.birthday != null)
        findViewById<TextView>(R.id.person_birthday).apply {
            text = person.birthday
            show(person.birthday != null)
        }
        findViewById<TextView>(R.id.person_place).apply {
            text = person.placeOfBirth
            show(person.placeOfBirth != null)
        }
        findViewById<View>(R.id.person_bio_group).show(person.biography.isNotBlank())
        val bio = findViewById<TextView>(R.id.person_bio)
        bio.text = person.biography
        if (person.biography.isNotBlank()) bio.makeExpandable(findViewById(R.id.person_read_more), collapsedLines = 7)

        val sections = findViewById<LinearLayout>(R.id.person_sections)
        sections.removeAllViews()
        val open = { media: Media -> Navigator.details(this, media.id, media.type) }
        sections.addMediaRow("Movies", person.movies, open)
        sections.addMediaRow("TV Shows", person.tvShows, open)
    }
}
