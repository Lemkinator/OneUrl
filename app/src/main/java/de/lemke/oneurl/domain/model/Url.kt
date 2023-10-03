package de.lemke.oneurl.domain.model

import android.graphics.Bitmap
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class Url(
    val shortUrl: String,
    val longUrl: String,
    val shortUrlProvider: ShortUrlProvider,
    val qr: Bitmap,
    val favorite: Boolean,
    val added: ZonedDateTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Url
        if (shortUrl != other.shortUrl) return false
        return true
    }

    override fun hashCode(): Int = shortUrl.hashCode()

    val addedFormatMedium: String
        get() = added.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

    fun getResizedQr(size: Int): Bitmap = Bitmap.createScaledBitmap(qr, size, size, false)

    fun containsKeywords(keywords: Set<String>): Boolean =
        keywords.any {
            shortUrl.contains(it, ignoreCase = true) ||
                    longUrl.contains(it, ignoreCase = true) ||
                    shortUrlProvider.toString().contains(it, ignoreCase = true) ||
                    added.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)).contains(it, ignoreCase = true)
        }
}
