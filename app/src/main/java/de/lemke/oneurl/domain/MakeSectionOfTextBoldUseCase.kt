package de.lemke.oneurl.domain

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

class MakeSectionOfTextBoldUseCase {
    operator fun invoke(text: String, textToBold: String?, color: Int): SpannableStringBuilder =
        invoke(text, textToBold?.trim(), color, -1)

    operator fun invoke(
        text: String,
        textToBold: String?,
        color: Int,
        lengthBefore: Int,
    ): SpannableStringBuilder {
        if (!textToBold.isNullOrEmpty()) {
            if (textToBold.trim().startsWith("\"") && textToBold.trim().endsWith("\"")) {
                if (textToBold.length > 2) {
                    val s = textToBold.substring(1, textToBold.length - 1)
                    return makeSectionOfTextBold(SpannableStringBuilder(text), hashSetOf(s.trim()), color, lengthBefore)
                }
            } else {
                return makeSectionOfTextBold(SpannableStringBuilder(text), HashSet(textToBold.trim().split(" ")), color, lengthBefore)
            }
        }
        return SpannableStringBuilder(text)
    }

    private fun makeSectionOfTextBold(builder: SpannableStringBuilder, textToBold: String, color: Int): SpannableStringBuilder {
        var text = builder.toString()
        if (textToBold.isEmpty() || !text.contains(textToBold, ignoreCase = true)) return builder
        var startingIndex = text.indexOf(textToBold, ignoreCase = true)
        var endingIndex = startingIndex + textToBold.length
        var offset = 0 //for multiple replaces
        var firstSearchIndex = text.length
        while (startingIndex >= 0) {
            builder.setSpan(StyleSpan(Typeface.BOLD_ITALIC), offset + startingIndex, offset + endingIndex, 0)
            builder.setSpan(ForegroundColorSpan(color), offset + startingIndex, offset + endingIndex, 0)
            //Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
            if (startingIndex < firstSearchIndex) firstSearchIndex = startingIndex
            text = text.substring(endingIndex)
            offset += endingIndex
            startingIndex = text.indexOf(textToBold, ignoreCase = true)
            endingIndex = startingIndex + textToBold.length
        }
        return builder
    }

    private fun makeSectionOfTextBold(
        spannableStringBuilder: SpannableStringBuilder,
        textsToBold: HashSet<String>,
        color: Int,
        lengthBefore: Int
    ): SpannableStringBuilder {
        var builder = spannableStringBuilder
        val text = builder.toString()
        var firstSearchIndex = text.length
        for (textItem in textsToBold) {
            if (text.contains(textItem, ignoreCase = true)) {
                firstSearchIndex = text.indexOf(textItem, ignoreCase = true)
                builder = makeSectionOfTextBold(builder, textItem, color)
            }
        }
        val start = 0.coerceAtLeast(firstSearchIndex - lengthBefore)
        return if (firstSearchIndex != text.length && lengthBefore >= 0 && start > 0) builder.replace(0, start, "...")
        else builder
    }
}