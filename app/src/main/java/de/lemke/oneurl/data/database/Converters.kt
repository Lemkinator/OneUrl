/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.data.database

import android.util.Log
import androidx.room.TypeConverter
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Type converters to map between SQLite types and entity types. */
object Converters {
    /** Returns the string representation of the [zonedDateTime]. */
    @TypeConverter
    fun zonedDateTimeToDb(zonedDateTime: ZonedDateTime?): String = zonedDateTime.toString()

    /** Returns the [ZonedDateTime] represented by the [zonedDateTimeString]. */
    @TypeConverter
    fun zonedDateTimeFromDb(zonedDateTimeString: String?): ZonedDateTime? {
        if (zonedDateTimeString == null) return null
        return try {
            ZonedDateTime.parse(zonedDateTimeString)
        } catch (e: DateTimeParseException) {
            Log.e("Converters", "Failed to parse ZonedDateTime from db value: $zonedDateTimeString", e)
            null
        }
    }
}
