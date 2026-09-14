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

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AppDatabaseMigrationTest (androidTest) proves the SQL migration is correct but runs outside
// Kover's testDebugUnitTest measurement. This builds a real file-backed database through
// Room.databaseBuilder instead of the in-memory builder every other JVM test uses, so opening it
// exercises the generated open/upgrade/validate path (AppDatabase_Impl's open delegate, both
// generated AutoMigration_*_Impl classes) that an in-memory build-at-latest-version never runs.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class AppDatabaseAutoMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `opening a version 1 database runs both auto-migrations and preserves rows`() =
        runTest {
            val dbFile = context.getDatabasePath("auto-migration-test.db")
            dbFile.parentFile?.mkdirs()
            dbFile.delete()
            SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { seed ->
                seed.execSQL(
                    "CREATE TABLE IF NOT EXISTS `url` (`shortURL` TEXT NOT NULL, `longURL` TEXT NOT NULL, " +
                        "`shortURLProvider` TEXT NOT NULL, `qr` BLOB NOT NULL, `favorite` INTEGER NOT NULL, " +
                        "`description` TEXT NOT NULL, `added` TEXT NOT NULL, PRIMARY KEY(`shortURL`))",
                )
                seed.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                seed.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES" +
                        "(42, 'e391e6edf552e60bd2561e2187493ba8')",
                )
                seed.execSQL(
                    "INSERT INTO url (shortURL, longURL, shortURLProvider, qr, favorite, description, added) " +
                        "VALUES ('https://short.url/x', 'https://example.com', 'da.gd', X'', 0, 'desc', " +
                        "'2024-01-15T10:30:00Z')",
                )
                seed.version = 1
            }

            val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.path).build()
            try {
                db.urlDao().getAll() shouldBe
                    listOf(
                        URLDb(
                            shortURL = "https://short.url/x",
                            longURL = "https://example.com",
                            shortURLProvider = "da.gd",
                            favorite = false,
                            title = "",
                            description = "desc",
                            added = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC),
                        ),
                    )
            } finally {
                db.close()
                dbFile.delete()
            }
        }
}
