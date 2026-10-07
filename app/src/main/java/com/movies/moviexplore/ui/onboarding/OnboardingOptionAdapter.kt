package com.movies.moviexplore.ui.onboarding

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.ui.common.inflate

/** A language or country choice: [badge] is a short code ("HI") or a flag; [subtitle] is optional. */
class OnboardingOption(val code: String, val badge: String, val title: String, val subtitle: String?)

/** Rows of `item_onboarding_option`; the one whose code is [selected] is highlighted and checked. */
class OnboardingOptionAdapter : BaseAdapter() {

    private var options: List<OnboardingOption> = emptyList()
    private var selected: String? = null

    fun submit(newOptions: List<OnboardingOption>, selectedCode: String) {
        options = newOptions
        selected = selectedCode
        notifyDataSetChanged()
    }

    override fun getCount() = options.size

    override fun getItem(position: Int) = options[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: parent.inflate(R.layout.item_onboarding_option)
        val option = options[position]
        val isSelected = option.code == selected
        view.findViewById<View>(R.id.option_card).setBackgroundResource(
            if (isSelected) R.drawable.bg_onboarding_option_selected else R.drawable.bg_onboarding_option
        )
        view.findViewById<TextView>(R.id.option_badge).apply {
            text = option.badge
            // A flag emoji needs more room than a two-letter code to read well.
            textSize = if (option.badge.all { it in 'A'..'Z' }) 14f else 22f
        }
        view.findViewById<TextView>(R.id.option_title).text = option.title
        view.findViewById<TextView>(R.id.option_subtitle).apply {
            text = option.subtitle
            visibility = if (option.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<View>(R.id.option_mark).setBackgroundResource(
            if (isSelected) R.drawable.bg_onboarding_check else R.drawable.bg_onboarding_radio
        )
        view.findViewById<View>(R.id.option_check).visibility = if (isSelected) View.VISIBLE else View.GONE
        view.contentDescription = listOfNotNull(option.title, option.subtitle, if (isSelected) "selected" else null).joinToString(", ")
        return view
    }
}
