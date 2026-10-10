package com.movies.moviexplore.ui.onboarding

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.ContentLocale
import com.movies.moviexplore.data.model.Country
import com.movies.moviexplore.ui.common.ItemAdapter
import com.movies.moviexplore.ui.common.scrollToTop
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

/** A language or country choice: [badge] is a short code ("HI") or a flag; [subtitle] is optional. */
class OnboardingOption(val code: String, val badge: String, val title: String, val subtitle: String?)

/**
 * A list of choice cards (`item_onboarding_option`) in [view]'s RecyclerView `choice_list`, with its search box
 * (`choice_search`) shown when [searchable]. [onSelect] gets the code of each card the user taps.
 */
class ChoiceList(
    view: View,
    private val options: List<OnboardingOption>,
    private var selected: String,
    searchable: Boolean,
    private val onSelect: (String) -> Unit,
) {
    private val list = view.findViewById<RecyclerView>(R.id.choice_list)
    private val adapter = ItemAdapter<Pair<OnboardingOption, Boolean>>(R.layout.item_onboarding_option, key = { it.first.code }) {
        card, (option, isSelected) -> bindCard(card, option, isSelected)
    }
    private var shown = options

    init {
        list.adapter = adapter
        if (searchable) {
            view.findViewById<View>(R.id.choice_search).show(true)
            view.findViewById<EditText>(R.id.choice_search_input).doAfterTextChanged { text ->
                val q = text?.toString().orEmpty().trim()
                shown = if (q.isEmpty()) options else options.filter {
                    it.title.contains(q, ignoreCase = true) || it.subtitle?.contains(q, ignoreCase = true) == true
                }
                render { list.scrollToTop(0) }
            }
        }
        // Start with the selected choice in view (e.g. a country deep in the list).
        render {
            shown.indexOfFirst { it.code == selected }.takeIf { it > 2 }?.let { list.scrollToTop(it - 2) }
        }
    }

    /** Shows the [shown] options, each marked whether it is the [selected] one. */
    private fun render(onShown: (() -> Unit)? = null) =
        adapter.submit(shown.map { it to (it.code == selected) }, onShown)

    /** The highlighted card with a check for the selected choice, a plain card with a radio otherwise. */
    private fun bindCard(card: View, option: OnboardingOption, isSelected: Boolean) {
        card.findViewById<View>(R.id.option_card).setBackgroundResource(
            if (isSelected) R.drawable.bg_onboarding_option_selected else R.drawable.bg_onboarding_option
        )
        card.findViewById<TextView>(R.id.option_badge).apply {
            text = option.badge
            // A flag emoji needs more room than a two-letter code to read well.
            textSize = if (option.badge.all { it in 'A'..'Z' }) 14f else 22f
        }
        card.findViewById<TextView>(R.id.option_title).text = option.title
        card.findViewById<TextView>(R.id.option_subtitle).apply {
            text = option.subtitle
            show(!option.subtitle.isNullOrEmpty())
        }
        card.findViewById<View>(R.id.option_mark).setBackgroundResource(
            if (isSelected) R.drawable.bg_onboarding_check else R.drawable.bg_onboarding_radio
        )
        card.findViewById<View>(R.id.option_check).show(isSelected)
        card.contentDescription = listOfNotNull(option.title, option.subtitle, if (isSelected) "selected" else null).joinToString(", ")
        card.setOnClickListener {
            selected = option.code
            render()
            onSelect(selected)
        }
    }
}
