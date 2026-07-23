package de.lemke.oneurl.domain.model

import android.graphics.Bitmap
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.StringTokenizer

data class URL(
    val shortURL: String,
    val longURL: String,
    val shortURLProvider: ShortURLProvider,
    val qr: Bitmap,
    val favorite: Boolean,
    val title: String,
    val description: String,
    val added: ZonedDateTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as URL
        return shortURL == other.shortURL &&
            longURL == other.longURL &&
            shortURLProvider == other.shortURLProvider &&
            favorite == other.favorite &&
            title == other.title &&
            description == other.description &&
            added == other.added
    }

    override fun hashCode(): Int {
        var result = shortURL.hashCode()
        result = 31 * result + longURL.hashCode()
        result = 31 * result + shortURLProvider.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + added.hashCode()
        return result
    }

    val id: Long get() = shortURL.hashCode().toLong()

    val alias: String
        get() = shortURL.trimEnd('/').substringAfterLast('/').substringBeforeLast('/') // does not work for Owovc(e.g. sketchy)

    val addedFormatMedium: String get() = added.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

    fun contains(query: String): Boolean = contains(StringTokenizer(query))

    fun contains(stringTokenizer: StringTokenizer): Boolean {
        while (stringTokenizer.hasMoreTokens()) {
            val nextToken = stringTokenizer.nextToken()
            if (shortURL.contains(nextToken, ignoreCase = true) ||
                longURL.contains(nextToken, ignoreCase = true) ||
                shortURLProvider.name.contains(nextToken, ignoreCase = true) ||
                title.contains(nextToken, ignoreCase = true) ||
                description.contains(nextToken, ignoreCase = true) ||
                addedFormatMedium.contains(nextToken, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }
}
