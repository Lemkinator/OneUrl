package de.lemke.oneurl.data.database

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.ZonedDateTime

@Entity(tableName = "url")
data class URLDb(
    @PrimaryKey
    val shortURL: String,
    val longURL: String,
    val shortURLProvider: String,
    val qr: Bitmap,
    val favorite: Boolean,
    val description: String,
    val added: ZonedDateTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as URLDb

        if (shortURL != other.shortURL) return false

        return true
    }

    override fun hashCode(): Int {
        return shortURL.hashCode()
    }
}
