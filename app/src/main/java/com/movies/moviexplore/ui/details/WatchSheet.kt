package com.movies.moviexplore.ui.details

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.movies.moviexplore.R
import com.movies.moviexplore.data.model.Country
import com.movies.moviexplore.data.model.TmdbImage
import com.movies.moviexplore.data.model.WatchOffer
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.ItemAdapter
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.showBottomSheet

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
    private var offerAdapter = newOfferAdapter()

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
        val offersRow = view.findViewById<RecyclerView>(R.id.watch_offers)
        offersRow.show(!loading && offers.isNotEmpty())

        // A new country (or new data) starts the row at its first service.
        val offersKey = state.country to state.availability
        if (offersKey != renderedOffers) {
            renderedOffers = offersKey
            offerAdapter.submit(offers) { offersRow.scrollToPosition(0) }
        }
    }

    /** Streaming service cards: logo, name, and how it offers the title. */
    private fun newOfferAdapter() = ItemAdapter<WatchOffer>(R.layout.item_offer, key = { it }) { card, offer ->
        card.findViewById<View>(R.id.offer_logo_frame).clipToOutline = true
        ImageLoader.load(card.findViewById(R.id.offer_logo), TmdbImage.logo(offer.logoPath))
        card.findViewById<TextView>(R.id.offer_name).text = offer.providerName
        card.findViewById<TextView>(R.id.offer_type).text = offer.type.label
        card.contentDescription = "${offer.providerName}, ${offer.type.label}"
        card.setOnClickListener { onOpenOffer(offer) }
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
        // A new sheet each time it opens, so a fresh adapter for its row.
        offerAdapter = newOfferAdapter()
        view.findViewById<RecyclerView>(R.id.watch_offers).adapter = offerAdapter
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
