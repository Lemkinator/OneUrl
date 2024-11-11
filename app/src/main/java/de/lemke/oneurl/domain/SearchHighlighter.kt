@file:Suppress("NOTHING_TO_INLINE")

package de.lemke.oneurl.domain

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_MARK_MARK
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import androidx.core.text.clearSpans
import de.lemke.oneurl.R
import java.util.StringTokenizer

class SearchHighlighter(
    @JvmField
    @ColorInt
    var highlightColor: Int = Color.parseColor("#2196F3")
) {
    constructor(context: Context) : this(
        highlightColor = context.getColor(R.color.primary_color_themed)
    )

    inline operator fun invoke(
        textToSearch: CharSequence,
        query: String,
        color: Int = highlightColor,
    ): SpannableString {
        return invoke(SpannableString(textToSearch), StringTokenizer(query), color)
    }

    inline operator fun invoke(
        spannableString: SpannableString,
        query: String,
        color: Int = highlightColor,
    ): SpannableString {
        if (query.isBlank()) return spannableString
        return invoke(spannableString, StringTokenizer(query), color)
    }

    inline operator fun invoke(
        spannableStringBuilder: SpannableStringBuilder,
        query: String,
        color: Int = highlightColor,
    ): SpannableStringBuilder {
        if (query.isBlank()) return spannableStringBuilder
        return invoke(spannableStringBuilder, StringTokenizer(query), color)
    }

    inline operator fun invoke(
        spannableStringBuilder: SpannableStringBuilder,
        searchTokens: StringTokenizer,
        color: Int = highlightColor,
    ): SpannableStringBuilder {
        spannableStringBuilder.clearSpans()
        while (searchTokens.hasMoreTokens()) {
            val nextToken = searchTokens.nextToken()
            var remainingString = spannableStringBuilder.toString()
            var offsetEnd = 0
            do {
                val index = remainingString.indexOf(nextToken, ignoreCase = true)
                if (index < 0) break
                val length = index + nextToken.length
                val offsetStart = index + offsetEnd
                offsetEnd += length
                spannableStringBuilder.setSpan(ForegroundColorSpan(color), offsetStart, offsetEnd, SPAN_MARK_MARK)
                remainingString = remainingString.substring(length)
            } while (offsetEnd < MAX_OFFSET)
        }
        return spannableStringBuilder
    }

    inline operator fun invoke(
        spannableString: SpannableString,
        searchTokens: StringTokenizer,
        color: Int = highlightColor,
    ): SpannableString {
        spannableString.clearSpans()
        while (searchTokens.hasMoreTokens()) {
            val nextToken = searchTokens.nextToken()
            var remainingString = spannableString.toString()
            var offsetEnd = 0
            do {
                val index = remainingString.indexOf(nextToken, ignoreCase = true)
                if (index < 0) break
                val length = index + nextToken.length
                val offsetStart = index + offsetEnd
                offsetEnd += length
                spannableString.setSpan(ForegroundColorSpan(color), offsetStart, offsetEnd, SPAN_MARK_MARK)
                remainingString = remainingString.substring(length)
            } while (offsetEnd < MAX_OFFSET)
        }
        return spannableString
    }

    companion object {
        const val MAX_OFFSET = 200
    }
}