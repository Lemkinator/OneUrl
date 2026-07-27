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
        val searchableFields = listOf(shortURL, longURL, shortURLProvider.name, title, description, addedFormatMedium)
        while (stringTokenizer.hasMoreTokens()) {
            val nextToken = stringTokenizer.nextToken()
            if (searchableFields.any { it.contains(nextToken, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }
}
