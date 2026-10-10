package com.movies.moviexplore.textview

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import kotlin.random.Random

/**
 * A TextView that justifies its text: every full line is widened to the view's width by sprinkling thin
 * hair spaces between the words. The last line of each paragraph stays left-aligned. Used in XML for the
 * overview and biography texts.
 */
class TextViewStyler @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : TextView(context, attrs, defStyleAttr) {

    private var lineWidth = 0
    private var justifiedText = ""

    override fun onDraw(canvas: Canvas) {
        val raw = text?.toString().orEmpty()
        // Justify once per new text, when the width is known; setText() then redraws with the result.
        if (raw != justifiedText && layoutParams.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
            lineWidth = measuredWidth - paddingLeft - paddingRight
            if (lineWidth > 0 && raw.isNotEmpty()) {
                justifiedText = justify(raw)
                if (justifiedText.isNotEmpty()) {
                    text = justifiedText
                    return
                }
            }
        }
        super.onDraw(canvas)
    }

    /** Splits [text] into lines that fit; full lines are justified, a paragraph's last line is not. */
    private fun justify(text: String): String {
        val lines = mutableListOf<String>()
        val line = mutableListOf<String>()
        val tokens = text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", " \n ").split(" ")

        for (token in tokens) {
            if (token.isEmpty()) continue
            if (token == "\n") {
                if (line.isNotEmpty()) lines += line.joinToString(SPACE)
                line.clear()
                lines += "\n"
                continue
            }
            if (line.isEmpty() || fits((line + token).joinToString(SPACE))) {
                line += token
            } else {
                lines += justifyLine(line)
                line.clear()
                line += token
            }
        }
        if (line.isNotEmpty()) lines += line.joinToString(SPACE)

        return buildString {
            lines.forEachIndexed { i, current ->
                append(current)
                if (current != "\n" && i + 1 < lines.size && lines[i + 1] != "\n") append("\n")
            }
        }
    }

    /** Adds hair spaces at random gaps between the words until one more wouldn't fit. */
    private fun justifyLine(words: List<String>): String {
        if (words.size <= 1) return words.joinToString(SPACE)
        val parts = mutableListOf<String>()
        words.forEachIndexed { i, word ->
            parts += word
            if (i < words.size - 1) parts += SPACE
        }
        while (fits(parts.joinToString("") + HAIR_SPACE)) {
            parts.add(Random.nextInt(1, maxOf(2, parts.size - 1)), HAIR_SPACE)
        }
        return parts.joinToString("")
    }

    private fun fits(candidate: String) = paint.measureText(candidate) <= lineWidth

    private companion object {
        const val SPACE = " "
        const val HAIR_SPACE = " "
    }
}
