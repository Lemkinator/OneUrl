package de.lemke.oneurl.domain.model

import android.graphics.Bitmap
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class URL(
    val shortURL: String,
    val longURL: String,
    val shortURLProvider: ShortURLProvider,
    val qr: Bitmap,
    val favorite: Boolean,
    val description: String,
    val added: ZonedDateTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as URL
        return shortURL == other.shortURL
    }

    override fun hashCode(): Int = shortURL.hashCode()

    val alias: String
        get() = shortURL.substringAfterLast('/')

    val addedFormatMedium: String
        get() = added.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

    fun containsKeywords(keywords: Set<String>): Boolean =
        keywords.any {
            shortURL.contains(it, ignoreCase = true) ||
                    longURL.contains(it, ignoreCase = true) ||
                    shortURLProvider.toString().contains(it, ignoreCase = true) ||
                    description.contains(it, ignoreCase = true) ||
                    added.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)).contains(it, ignoreCase = true)
        }
}
