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

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Non-destructive migration coverage: PersistenceModule has no fallbackToDestructiveMigration, so
// a broken auto-migration is a hard IllegalStateException for every user on upgrade. The
// TestPersistenceModule Hilt override used elsewhere always builds an in-memory database, which
// never runs migrations at all - this test is the only thing that exercises the real 2->3 path.
// MigrationTestHelper reads the exported schema json (app/schemas, wired as an androidTest asset
// dir in build.gradle.kts) to build the version-2 starting database, so this runs as an
// instrumented test rather than a Robolectric one.
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            listOf(AppDatabase.DeleteQrColumn()),
        )

    @Test
    fun migrate2To3DropsTheQrColumnAndKeepsExistingRows() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO url (shortURL, longURL, shortURLProvider, qr, favorite, title, description, added) " +
                    "VALUES ('https://short.url/x', 'https://example.com', 'Dagd', X'', 0, 'title', 'desc', " +
                    "'2024-01-15T10:30:00Z')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        db.query("SELECT shortURL, longURL, favorite, title, description, added FROM url").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("shortURL")) shouldBe "https://short.url/x"
            cursor.getString(cursor.getColumnIndexOrThrow("longURL")) shouldBe "https://example.com"
            cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) shouldBe 0
            cursor.getString(cursor.getColumnIndexOrThrow("title")) shouldBe "title"
            cursor.getString(cursor.getColumnIndexOrThrow("description")) shouldBe "desc"
            cursor.getString(cursor.getColumnIndexOrThrow("added")) shouldBe "2024-01-15T10:30:00Z"
        }
        db.query("PRAGMA table_info(url)").use { cursor ->
            val columnNames = generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }
            columnNames.toList() shouldBe listOf("shortURL", "longURL", "shortURLProvider", "favorite", "title", "description", "added")
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
