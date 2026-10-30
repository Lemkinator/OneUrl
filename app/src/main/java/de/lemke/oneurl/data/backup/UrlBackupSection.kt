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

import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.commonutils.backup.core.MergeableSection
import de.lemke.oneurl.data.database.URLDao
import de.lemke.oneurl.data.database.URLDb
import java.time.DateTimeException
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

class UrlBackupSection @Inject constructor(
    private val dao: URLDao,
) : MergeableSection<UrlBackupEntry, String> {
    override val id: String = ID
    override val version: Int = 1
    override val minReadableVersion: Int = 1
    override val serializer: KSerializer<UrlBackupEntry> = UrlBackupEntry.serializer()

    override fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject = error("The $ID section has no format before version $version, cannot upgrade from $fromVersion")

    override fun validate(entry: UrlBackupEntry): EntryCheck =
        when {
            entry.shortURL.isBlank() -> EntryCheck.Invalid("shortURL is blank")
            entry.longURL.isBlank() -> EntryCheck.Invalid("longURL is blank")
            !entry.hasOffsetStamp() -> EntryCheck.Invalid("added is not an ISO-8601 date-time with an offset")
            else -> EntryCheck.Valid
        }

    override suspend fun exportEntries(): List<UrlBackupEntry> = dao.getAll().map(URLDb::toBackupEntry)

    override suspend fun clear() = dao.deleteAll()

    override suspend fun insert(entries: List<UrlBackupEntry>) = dao.insertAll(entries.map(UrlBackupEntry::toDb))

    override fun key(entry: UrlBackupEntry): String = entry.shortURL

    override fun resolve(
        local: UrlBackupEntry,
        incoming: UrlBackupEntry,
    ): UrlBackupEntry = local.copy(favorite = local.favorite || incoming.favorite)

    override suspend fun localEntries(keys: Set<String>): Map<String, UrlBackupEntry> =
        keys
            .chunked(LOOKUP_CHUNK_SIZE)
            .flatMap { dao.getByShortURLs(it) }
            .associate { it.shortURL to it.toBackupEntry() }

    override suspend fun replace(
        local: UrlBackupEntry,
        merged: UrlBackupEntry,
    ) = dao.setFavorite(merged.shortURL, merged.favorite)

    companion object {
        const val ID = "urls"
        internal const val LOOKUP_CHUNK_SIZE = 500
    }
}

private fun UrlBackupEntry.hasOffsetStamp(): Boolean =
    try {
        addedInSystemZone()
        true
    } catch (ignored: DateTimeException) {
        false
    }
