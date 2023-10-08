package de.lemke.oneurl.data.database

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.ZonedDateTime

@Entity(tableName = "url")
data class UrlDb(
    @PrimaryKey
    val shortUrl: String,
    val longUrl: String,
    val shortUrlProvider: String,
    val qr: Bitmap,
    val favorite: Boolean,
    val description: String,
    val added: ZonedDateTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UrlDb

        if (shortUrl != other.shortUrl) return false

        return true
    }

    override fun hashCode(): Int {
        return shortUrl.hashCode()
    }
}
