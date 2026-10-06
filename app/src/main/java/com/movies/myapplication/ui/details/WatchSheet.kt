package com.movies.myapplication.ui.details

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.movies.myapplication.R
import com.movies.myapplication.data.model.Country
import com.movies.myapplication.data.model.TmdbImage
import com.movies.myapplication.data.model.WatchOffer
import com.movies.myapplication.ui.common.ImageLoader
import com.movies.myapplication.ui.common.inflate
import com.movies.myapplication.ui.common.show
import com.movies.myapplication.ui.common.showBottomSheet

/**
 * The Watch Availability bottom sheet: a country dropdown (every country TMDB has data for) and the
 * services that stream, rent or sell the title there. It mirrors [WatchPanelState]: opening, closing
 * and content all follow the store.
 */
class WatchAvailabilitySheet(
    private val activity: Activity,
    private val onSelectCountry: (String) -> Unit,
    private val onRetry: () -> Unit,
    private val onOpenOffer: (WatchOffer) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var dialog: Dialog? = null
    private var sheet: View? = null
    private val countryAdapter = CountryAdapter()
    private var shownCountry: String? = null
    private var renderedOffers: Any? = null

    fun render(state: WatchPanelState) {
        if (!state.isOpen) {
            dialog?.dismiss()
            return
        }
        if (dialog == null) {
            dialog = activity.showBottomSheet(R.layout.dialog_watch, onDismiss = {
                dialog = null
                sheet = null
                renderedOffers = null
                onDismiss()
            }) { _, view -> setUp(view) }
        }
        val view = sheet ?: return

        val hasCountries = state.countries.isNotEmpty()
        view.findViewById<View>(R.id.watch_country_label).show(hasCountries)
        view.findViewById<View>(R.id.watch_country_field).show(hasCountries)
        countryAdapter.submit(state.countries, state.country)
        shownCountry = state.country
        val spinner = view.findViewById<Spinner>(R.id.watch_country)
        val index = state.countries.indexOfFirst { it.code == state.country }
        if (index >= 0 && spinner.selectedItemPosition != index) spinner.setSelection(index, false)
        spinner.contentDescription = "Country: ${state.selectedCountry.name}"

        val offers = state.current?.offers.orEmpty()
        val loading = state.isLoading || (state.availability == null && state.error == null)
        val message = when {
            loading -> null
            state.error != null -> state.error
            offers.isEmpty() -> "Not available to stream, rent or buy in ${state.selectedCountry.name} yet."
            else -> null
        }
        view.findViewById<View>(R.id.watch_loading).show(loading)
        view.findViewById<View>(R.id.watch_message_group).show(message != null)
        view.findViewById<TextView>(R.id.watch_message).text = message
        view.findViewById<View>(R.id.watch_retry).show(!loading && state.error != null)
        view.findViewById<View>(R.id.watch_offers_scroll).show(!loading && offers.isNotEmpty())

        val offersKey = state.country to state.availability
        if (offersKey != renderedOffers) {
            renderedOffers = offersKey
            val row = view.findViewById<LinearLayout>(R.id.watch_offers)
            row.removeAllViews()
            offers.forEach { offer -> row.addView(offerCard(row, offer)) }
            view.findViewById<View>(R.id.watch_offers_scroll).scrollTo(0, 0)
        }
    }

    private fun setUp(view: View) {
        sheet = view
        val spinner = view.findViewById<Spinner>(R.id.watch_country)
        spinner.adapter = countryAdapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) {
                val code = countryAdapter.getItem(position).code
                // Programmatic selections (matching the state) are ignored; only real picks are reported.
                if (code != shownCountry) onSelectCountry(code)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        view.findViewById<View>(R.id.watch_retry).setOnClickListener { onRetry() }
    }

    private fun offerCard(parent: ViewGroup, offer: WatchOffer): View = parent.inflate(R.layout.item_offer).apply {
        findViewById<View>(R.id.offer_logo_frame).clipToOutline = true
        ImageLoader.load(findViewById(R.id.offer_logo), TmdbImage.logo(offer.logoPath))
        findViewById<TextView>(R.id.offer_name).text = offer.providerName
        findViewById<TextView>(R.id.offer_type).text = offer.type.label
        contentDescription = "${offer.providerName}, ${offer.type.label}"
        setOnClickListener { onOpenOffer(offer) }
    }

    /** Countries for the Spinner: flag and name, with the current one highlighted in the open list. */
    private class CountryAdapter : BaseAdapter() {
        private var countries: List<Country> = emptyList()
        private var selectedCode: String? = null

        fun submit(newCountries: List<Country>, selected: String) {
            if (newCountries == countries && selected == selectedCode) return
            countries = newCountries
            selectedCode = selected
            notifyDataSetChanged()
        }

        override fun getCount() = countries.size

        override fun getItem(position: Int) = countries[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            label(convertView, parent, countries[position], highlighted = false)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            label(convertView, parent, countries[position], highlighted = countries[position].code == selectedCode)

        private fun label(convertView: View?, parent: ViewGroup, country: Country, highlighted: Boolean): View {
            val view = (convertView ?: parent.inflate(R.layout.item_country)) as TextView
            view.text = "${country.flag}   ${country.name}"
            view.setTextColor(view.context.getColor(if (highlighted) R.color.primary else R.color.text_primary))
            view.setBackgroundColor(if (highlighted) view.context.getColor(R.color.primary_tint) else 0)
            return view
        }
    }
}
