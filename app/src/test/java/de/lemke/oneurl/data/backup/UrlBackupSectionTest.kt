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

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.exportFiles
import de.lemke.oneurl.data.database.AppDatabase
import de.lemke.oneurl.data.database.URLDb
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UrlBackupSectionTest : UrlBackupSectionContract() {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val section = UrlBackupSection(database.urlDao())
    private val transaction = RoomBackupTransaction(database)
    private val engine =
        BackupEngine(
            setOf(section),
            transaction,
            BackupApp("oneurl", 45, BackupFileExtension(".oneurl")),
            { "2026-10-27T14:30:00+01:00" },
        )
    private val originalZone = TimeZone.getDefault()

    @Before
    fun useBerlinZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BERLIN))
    }

    @After
    fun closeDatabaseAndRestoreZone() {
        database.close()
        TimeZone.setDefault(originalZone)
    }

    override fun section(): BackupSection<UrlBackupEntry> = section

    override fun transaction(): BackupTransaction = transaction

    private fun urlDb(
        shortURL: String,
        added: ZonedDateTime,
    ) = URLDb(shortURL, "https://example.com", "da.gd", favorite = false, title = "title", description = "description", added = added)

    private suspend fun readExport(): BackupReadResult.Ready {
        val result = engine.read(engine.exportFiles().toMutableMap())
        result.shouldBeInstanceOf<BackupReadResult.Ready>()
        return result
    }

    private suspend fun storeUrls(count: Int) = section.insert(List(count) { entry("https://da.gd/$it") })

    @Test
    fun `merge of 1,200 new urls adds all of them`(): Unit =
        runBlocking {
            storeUrls(1_200)
            val ready = readExport()
            section.clear()

            val report = engine.restore(ready, ImportMode.MERGE).sections.single()

            report.added shouldBe 1_200
            database.urlDao().getAll().size shouldBe 1_200
        }

    @Test
    fun `merge of 1,200 stored urls keeps all of them`(): Unit =
        runBlocking {
            storeUrls(1_200)

            val report = engine.restore(readExport(), ImportMode.MERGE).sections.single()

            report.kept shouldBe 1_200
            report.added shouldBe 0
        }

    @Test
    fun `a lookup chunk stays within the SQLite default variable limit`() {
        (UrlBackupSection.LOOKUP_CHUNK_SIZE <= SQLITE_DEFAULT_VARIABLE_LIMIT) shouldBe true
    }

    @Test
    fun `a failure inside the transaction keeps every stored url`(): Unit =
        runBlocking {
            storeUrls(3)

            shouldThrow<IllegalStateException> {
                transaction.run {
                    database.urlDao().deleteAll()
                    error("x")
                }
            }

            database.urlDao().getAll().map { it.shortURL }.shouldContainExactlyInAnyOrder(
                "https://da.gd/0",
                "https://da.gd/1",
                "https://da.gd/2",
            )
        }

    @Test
    fun `export writes added with its offset`(): Unit =
        runBlocking {
            database.urlDao().insert(urlDb("https://da.gd/a", ZonedDateTime.of(2026, 10, 27, 14, 30, 0, 0, ZoneId.of("Asia/Tokyo"))))

            section.exportEntries().single().added shouldBe "2026-10-27T14:30:00+09:00"
        }

    @Test
    fun `import converts added to the device zone at the same instant`(): Unit =
        runBlocking {
            section.insert(listOf(entry("https://da.gd/a", added = "2026-10-27T14:30:00+09:00")))

            database
                .urlDao()
                .getAll()
                .single()
                .added shouldBe ZonedDateTime.of(2026, 10, 27, 6, 30, 0, 0, ZoneId.of(BERLIN))
        }

    @Test
    fun `an added without offset is broken`() {
        section.validate(entry("https://da.gd/a", added = "2026-10-27T14:30:00")) shouldBe
            EntryCheck.Invalid("added is not an ISO-8601 date-time with an offset")
    }

    @Test
    fun `a blank short url or long url is broken`() {
        section.validate(entry("  ")) shouldBe EntryCheck.Invalid("shortURL is blank")
        section.validate(entry("https://da.gd/a").copy(longURL = "")) shouldBe EntryCheck.Invalid("longURL is blank")
    }

    @Test
    fun `an unknown provider name is valid`() {
        section.validate(entry("https://da.gd/a").copy(shortURLProvider = "gone.example")) shouldBe EntryCheck.Valid
    }

    @Test
    fun `upgrade throws because version 1 has no earlier format`() {
        shouldThrow<IllegalStateException> { section.upgrade(1, JsonObject(emptyMap())) }
            .message shouldBe "The urls section has no format before version 1, cannot upgrade from 1"
    }

    private companion object {
        const val BERLIN = "Europe/Berlin"
        const val SQLITE_DEFAULT_VARIABLE_LIMIT = 999
    }
}
