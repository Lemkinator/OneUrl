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
        return shortURL == other.shortURL
    }

    override fun hashCode(): Int = shortURL.hashCode()

    val id get() = hashCode().toLong()

    fun contentEquals(other: URL): Boolean = shortURL == other.shortURL &&
            longURL == other.longURL &&
            shortURLProvider == other.shortURLProvider &&
            favorite == other.favorite &&
            title == other.title &&
            description == other.description &&
            added == other.added

    val alias: String get() = shortURL.trimEnd('/').substringAfterLast('/').substringBeforeLast('/') //does not work for Owovc(e.g. sketchy)

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
            ) return true
        }
        return false
    }
}
