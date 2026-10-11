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

package de.lemke.oneurl.data

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.Tinyurl
import de.lemke.oneurl.domain.model.URL
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Behaviour every [URLRepository] shares, so [FakeURLRepository] stays true to [DefaultURLRepository]. */
abstract class URLRepositoryContract {
    /** Runs every test; a repository must launch its notification work here for [settle] to wait for it. */
    protected val dispatcher = StandardTestDispatcher()

    abstract fun createRepository(): URLRepository

    private val first = url("https://da.gd/first")
    private val second = url("https://da.gd/second")
    private val third = url("https://da.gd/third")

    private suspend fun URLRepository.stored(): List<URL> = observeURLs().first()

    @Test
    fun `a new repository observes no urls`() =
        runTest(dispatcher) {
            createRepository().stored() shouldBe emptyList()
        }

    @Test
    fun `observeURLs lists added urls newest first`() =
        runTest(dispatcher) {
            val repository = createRepository()

            repository.addURL(first)
            repository.addURL(second)
            repository.addURL(third)

            repository.stored() shouldBe listOf(third, second, first)
        }

    @Test
    fun `getURL by short url returns the stored url or null`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)

            repository.getURL("https://da.gd/first") shouldBe first
            repository.getURL("https://da.gd/missing") shouldBe null
        }

    @Test
    fun `getURL by provider and long url lists the matching urls newest first`() =
        runTest(dispatcher) {
            val repository = createRepository()
            val otherLongURL = url("https://da.gd/other", longURL = "https://other.example.com")
            val otherProvider = url("https://tinyurl.com/x", provider = Tinyurl)
            repository.addURL(first)
            repository.addURL(otherLongURL)
            repository.addURL(otherProvider)
            repository.addURL(second)

            repository.getURL(Dagd, LONG_URL) shouldBe listOf(second, first)
            repository.getURL(Tinyurl, LONG_URL) shouldBe listOf(otherProvider)
            repository.getURL(Tinyurl, "https://other.example.com") shouldBe emptyList()
        }

    @Test
    fun `addURL of a stored short url replaces it and makes it the newest`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)
            repository.addURL(second)
            val replacement = first.copy(title = "replaced")

            repository.addURL(replacement)

            repository.stored() shouldBe listOf(replacement, second)
        }

    @Test
    fun `updateURL replaces the stored url in place`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)
            repository.addURL(second)
            val favorite = first.copy(favorite = true)

            repository.updateURL(favorite)

            repository.stored() shouldBe listOf(second, favorite)
        }

    @Test
    fun `updateURL of an unknown short url stores nothing`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)

            repository.updateURL(second)

            repository.stored() shouldBe listOf(first)
        }

    @Test
    fun `updateURLs replaces every given url in place`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)
            repository.addURL(second)
            repository.addURL(third)
            val renamedFirst = first.copy(title = "first")
            val renamedThird = third.copy(title = "third")

            repository.updateURLs(listOf(renamedFirst, renamedThird))

            repository.stored() shouldBe listOf(renamedThird, second, renamedFirst)
        }

    @Test
    fun `deleteURL removes the url with the same short url`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)
            repository.addURL(second)

            repository.deleteURL(first.copy(title = "stale copy"))

            repository.stored() shouldBe listOf(second)
            repository.getURL("https://da.gd/first") shouldBe null
        }

    @Test
    fun `deleteURLs removes every given url and ignores unknown ones`() =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.addURL(first)
            repository.addURL(second)
            repository.addURL(third)

            repository.deleteURLs(listOf(first, third, url("https://da.gd/missing")))

            repository.stored() shouldBe listOf(second)
        }

    private fun observeWhileWriting(block: suspend ReceiveTurbine<List<URL>>.(URLRepository) -> Unit) =
        runTest(dispatcher) {
            val repository = createRepository()
            repository.observeURLs().test {
                awaitItem() shouldBe emptyList()
                repository.addURL(first)
                awaitItem() shouldBe listOf(first)
                repository.addURL(second)
                awaitItem() shouldBe listOf(second, first)
                block(repository)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeURLs emits after addURL, also for an unchanged url`() =
        observeWhileWriting { repository ->
            repository.addURL(third)
            awaitItem() shouldBe listOf(third, second, first)

            repository.addURL(third)
            awaitItem() shouldBe listOf(third, second, first)
        }

    @Test
    fun `observeURLs emits after updateURL, also for unchanged values`() =
        observeWhileWriting { repository ->
            val favorite = first.copy(favorite = true)
            repository.updateURL(favorite)
            awaitItem() shouldBe listOf(second, favorite)

            repository.updateURL(favorite)
            awaitItem() shouldBe listOf(second, favorite)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `observeURLs does not emit after updateURL of an unknown short url`() =
        observeWhileWriting { repository ->
            repository.updateURL(third)
            repository.updateURLs(listOf(third, url("https://da.gd/missing")))
            settle()

            expectNoEvents()
        }

    @Test
    fun `observeURLs emits after deleteURL and not after deleting an unknown short url`() =
        observeWhileWriting { repository ->
            repository.deleteURL(third)
            repository.deleteURLs(listOf(third))
            settle()
            expectNoEvents()

            repository.deleteURL(first)

            awaitItem() shouldBe listOf(second)
        }

    private companion object {
        const val LONG_URL = "https://example.com"

        fun url(
            shortURL: String,
            longURL: String = LONG_URL,
            provider: ShortURLProvider = Dagd,
        ) = URL(
            shortURL = shortURL,
            longURL = longURL,
            shortURLProvider = provider,
            favorite = false,
            title = "title",
            description = "description",
            added = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
        )
    }
}
