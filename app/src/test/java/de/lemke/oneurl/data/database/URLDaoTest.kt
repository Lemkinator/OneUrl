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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Exercises the real Room-generated DAO/database implementation against an in-memory database -
// every other test in this module mocks URLDao, so its own generated implementation (queries,
// converters, migrations wiring) is only ever compiled, never run, without this test.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class URLDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: URLDao

    private fun url(
        shortURL: String,
        longURL: String = "https://example.com",
        provider: String = "da.gd",
        favorite: Boolean = false,
    ) = URLDb(
        shortURL = shortURL,
        longURL = longURL,
        shortURLProvider = provider,
        favorite = favorite,
        title = "title",
        description = "description",
        added = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
    )

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.urlDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert then getURL by shortURL returns the same entity`() =
        runTest {
            val entity = url("https://short.url/abc")

            dao.insert(entity)

            dao.getURL("https://short.url/abc") shouldBe entity
        }

    @Test
    fun `getURL by shortURL returns null when nothing matches`() =
        runTest {
            dao.getURL("https://short.url/missing") shouldBe null
        }

    @Test
    fun `insert with a conflicting primary key replaces the existing row`() =
        runTest {
            dao.insert(url("https://short.url/abc", favorite = false))

            dao.insert(url("https://short.url/abc", favorite = true))

            dao.getURL("https://short.url/abc")?.favorite shouldBe true
        }

    @Test
    fun `getURL by provider and longURL returns only matching rows`() =
        runTest {
            val matching = url("https://short.url/a", longURL = "https://match.com", provider = "da.gd")
            dao.insert(matching)
            dao.insert(url("https://short.url/b", longURL = "https://other.com", provider = "da.gd"))
            dao.insert(url("https://short.url/c", longURL = "https://match.com", provider = "is.gd"))

            dao.getURL("da.gd", "https://match.com") shouldBe listOf(matching)
        }

    @Test
    fun `getAll returns every inserted row`() =
        runTest {
            val first = url("https://short.url/a")
            val second = url("https://short.url/b")
            dao.insert(first)
            dao.insert(second)

            dao.getAll() shouldContainExactly listOf(first, second)
        }

    @Test
    fun `observeAll emits the current rows`() =
        runTest {
            val entity = url("https://short.url/abc")
            dao.insert(entity)

            dao.observeAll().first() shouldBe listOf(entity)
        }

    @Test
    fun `update replaces the row's fields`() =
        runTest {
            val entity = url("https://short.url/abc", favorite = false)
            dao.insert(entity)

            dao.update(entity.copy(favorite = true))

            dao.getURL("https://short.url/abc")?.favorite shouldBe true
        }

    @Test
    fun `updateMultiple replaces every given row`() =
        runTest {
            val first = url("https://short.url/a", favorite = false)
            val second = url("https://short.url/b", favorite = false)
            dao.insert(first)
            dao.insert(second)

            dao.updateMultiple(listOf(first.copy(favorite = true), second.copy(favorite = true)))

            dao.getAll().all { it.favorite } shouldBe true
        }

    @Test
    fun `delete by shortURL removes only that row`() =
        runTest {
            val kept = url("https://short.url/keep")
            dao.insert(kept)
            dao.insert(url("https://short.url/remove"))

            dao.delete("https://short.url/remove")

            dao.getAll() shouldBe listOf(kept)
        }

    @Test
    fun `delete by list removes every given row`() =
        runTest {
            val kept = url("https://short.url/keep")
            val removed = listOf(url("https://short.url/a"), url("https://short.url/b"))
            dao.insert(kept)
            removed.forEach { dao.insert(it) }

            dao.delete(removed)

            dao.getAll() shouldBe listOf(kept)
        }

    @Test
    fun `deleteAll empties the table`() =
        runTest {
            dao.insert(url("https://short.url/a"))
            dao.insert(url("https://short.url/b"))

            dao.deleteAll()

            dao.getAll() shouldBe emptyList()
        }
}
