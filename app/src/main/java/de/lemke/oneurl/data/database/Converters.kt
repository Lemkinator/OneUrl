package de.lemke.oneurl.data.database

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime

/** Type converters to map between SQLite types and entity types. */
object Converters {
    /** Returns the string representation of the [zonedDateTime]. */
    @TypeConverter
    fun zonedDateTimeToDb(zonedDateTime: ZonedDateTime?): String = zonedDateTime.toString()


    /** Returns the [ZonedDateTime] represented by the [zonedDateTimeString]. */
    @TypeConverter
    fun zonedDateTimeFromDb(zonedDateTimeString: String?): ZonedDateTime? = try {
        ZonedDateTime.parse(zonedDateTimeString)
    } catch (e: Exception) {
        null
    }

    /** Returns the string representation of the [bitmap]. */
    @TypeConverter
    fun bitmapToDb(bitmap: Bitmap): ByteArray = with(ByteArrayOutputStream()) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        return toByteArray()
    }

    /** Returns the [Bitmap] represented by the [byteArray]. */
    @TypeConverter
    fun bitmapFromDb(byteArray: ByteArray): Bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}
