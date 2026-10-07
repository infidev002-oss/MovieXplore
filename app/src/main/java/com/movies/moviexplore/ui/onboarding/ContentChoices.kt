package com.movies.moviexplore.ui.onboarding

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ListView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.ContentLocale
import com.movies.moviexplore.data.model.Country
import com.movies.moviexplore.ui.common.show
import java.text.Collator
import java.util.Locale

/**
 * The content language and country choices, shared by onboarding and Account → Language / Country.
 */
object ContentChoices {

    /** "HI · Hindi · हिन्दी": code badge, the English name, then the language's own name when it differs. */
    fun languages(): List<OnboardingOption> = ContentLocale.languageCodes.map { code ->
        val locale = Locale.forLanguageTag(code)
        val english = locale.getDisplayLanguage(Locale.ENGLISH)
        val native = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
        OnboardingOption(code, code.uppercase(), english, native.takeUnless { it.equals(english, ignoreCase = true) })
    }

    /** Every ISO country with its flag, named in the device language and sorted alphabetically. */
    fun countries(): List<OnboardingOption> {
        val collator = Collator.getInstance()
        return Locale.getISOCountries()
            .map { code -> Country(code, countryName(code)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
            .map { OnboardingOption(it.code, it.flag, it.name, null) }
    }

    /** The language's English name, e.g. "Hindi". */
    fun languageName(code: String): String = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)

    /** The country's name in the device language, e.g. "India". */
    fun countryName(code: String): String = Locale.Builder().setRegion(code).build().displayCountry
}

/**
 * A list of choice cards (`item_onboarding_option`) in [view], with its search box (`choice_search`) shown when
 * [searchable]. [onSelect] gets the code of each card the user taps.
 */
class ChoiceList(
    view: View,
    private val options: List<OnboardingOption>,
    private var selected: String,
    searchable: Boolean,
    private val onSelect: (String) -> Unit,
) {
    private val adapter = OnboardingOptionAdapter()
    private var shown = options

    init {
        val list = view.findViewById<ListView>(R.id.choice_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            selected = shown[position].code
            render()
            onSelect(selected)
        }
        if (searchable) {
            view.findViewById<View>(R.id.choice_search).show(true)
            view.findViewById<EditText>(R.id.choice_search_input).addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString().orEmpty().trim()
                    shown = if (q.isEmpty()) options else options.filter {
                        it.title.contains(q, ignoreCase = true) || it.subtitle?.contains(q, ignoreCase = true) == true
                    }
                    render()
                    list.setSelection(0)
                }
            })
        }
        render()
        // Start with the selected choice in view (e.g. a country deep in the list).
        shown.indexOfFirst { it.code == selected }.takeIf { it > 2 }?.let { list.setSelection(it - 2) }
    }

    private fun render() = adapter.submit(shown, selected)
}
