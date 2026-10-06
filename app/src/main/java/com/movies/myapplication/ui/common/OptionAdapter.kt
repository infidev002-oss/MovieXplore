package com.movies.myapplication.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.movies.myapplication.R

/** One row in a choice list. */
data class Option(val label: String, val selected: Boolean)

/** A list of choices (`item_option` rows); the selected one is tinted and checked. */
class OptionAdapter : BaseAdapter() {

    private var options: List<Option> = emptyList()

    fun submit(newOptions: List<Option>) {
        options = newOptions
        notifyDataSetChanged()
    }

    override fun getCount() = options.size

    override fun getItem(position: Int) = options[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: parent.inflate(R.layout.item_option)
        val option = options[position]
        view.findViewById<TextView>(R.id.option_label).apply {
            text = option.label
            setTextColor(context.getColor(if (option.selected) R.color.primary else R.color.text_primary))
        }
        view.findViewById<View>(R.id.option_check).visibility = if (option.selected) View.VISIBLE else View.INVISIBLE
        view.isSelected = option.selected
        return view
    }
}
