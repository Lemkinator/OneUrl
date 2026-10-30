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

package de.lemke.oneurl.data.backup

import de.lemke.oneurl.data.database.URLDb
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

@Serializable
data class UrlBackupEntry(
    val shortURL: String,
    val longURL: String,
    val shortURLProvider: String,
    val favorite: Boolean,
    val title: String = "",
    val description: String,
    val added: String,
)

internal fun URLDb.toBackupEntry() =
    UrlBackupEntry(
        shortURL = shortURL,
        longURL = longURL,
        shortURLProvider = shortURLProvider,
        favorite = favorite,
        title = title,
        description = description,
        added = added.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

internal fun UrlBackupEntry.toDb() =
    URLDb(
        shortURL = shortURL,
        longURL = longURL,
        shortURLProvider = shortURLProvider,
        favorite = favorite,
        title = title,
        description = description,
        added = OffsetDateTime.parse(added).atZoneSameInstant(ZoneId.systemDefault()),
    )
