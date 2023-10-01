package de.lemke.oneurl.domain.model

import android.graphics.Bitmap
import java.time.ZonedDateTime

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

    fun containsKeywords(keywords: Set<String>): Boolean =
        keywords.any { shortUrl.contains(it, ignoreCase = true) || longUrl.contains(it, ignoreCase = true) }
}

enum class ShortUrlProvider {
    TINYURL,
    ISGD;

    override fun toString(): String = when (this) {
        TINYURL -> "tinyurl.com"
        ISGD -> "is.gd"
    }

    companion object {
        private val default = TINYURL

        private fun fromStringOrNull(string: String?): ShortUrlProvider? = when (string) {
            "tinyurl.com" -> TINYURL
            "is.gd" -> ISGD
            else -> null
        }

        fun fromString(string: String): ShortUrlProvider = with(fromStringOrNull(string)) {
            if (this != null) return@with this
            else throw IllegalArgumentException("Unknown ShortUrlProvider: $string")
        }

        fun fromStringOrDefault(string: String?): ShortUrlProvider = fromStringOrNull(string) ?: default

    }
}