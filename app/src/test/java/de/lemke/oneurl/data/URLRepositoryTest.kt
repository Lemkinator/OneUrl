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

import de.lemke.oneurl.data.database.URLDao
import de.lemke.oneurl.data.database.urlToDb
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

private fun testUrl(shortURL: String) =
    URL(
        shortURL = shortURL,
        longURL = "https://example.com",
        shortURLProvider = ShortURLProviderCompanion.default,
        favorite = false,
        title = "title",
        description = "description",
        added = ZonedDateTime.now(),
    )

class URLRepositoryTest : ShouldSpec(
    {
        val urlDao = mockk<URLDao>()
        val repository = URLRepository(urlDao)

        should("observeURLs maps the dao's flow in the dao's order") {
            val oldest = testUrl("https://short.url/oldest")
            val newest = testUrl("https://short.url/newest")
            every { urlDao.observeAll() } returns flowOf(listOf(urlToDb(newest), urlToDb(oldest)))

            val result = repository.observeURLs().toList().single()

            result shouldBe listOf(newest, oldest)
        }

        should("getURL(shortURL) returns the mapped url when the dao finds one") {
            val url = testUrl("https://short.url/abc")
            coEvery { urlDao.getURL("https://short.url/abc") } returns urlToDb(url)

            repository.getURL("https://short.url/abc") shouldBe url
        }

        should("getURL(shortURL) returns null when the dao finds nothing") {
            coEvery { urlDao.getURL("https://short.url/missing") } returns null

            repository.getURL("https://short.url/missing") shouldBe null
        }

        should("getURL(provider, longURL) maps the dao's list in the dao's order") {
            val provider = ShortURLProviderCompanion.default
            val oldest = testUrl("https://short.url/oldest")
            val newest = testUrl("https://short.url/newest")
            coEvery { urlDao.getURL(provider.name, "https://example.com") } returns listOf(urlToDb(newest), urlToDb(oldest))

            val result = repository.getURL(provider, "https://example.com")

            result shouldBe listOf(newest, oldest)
        }

        should("addURL inserts the mapped db entity") {
            val url = testUrl("https://short.url/abc")
            coEvery { urlDao.insert(urlToDb(url)) } returns Unit

            repository.addURL(url)

            coVerify(exactly = 1) { urlDao.insert(urlToDb(url)) }
        }

        should("updateURL updates the mapped db entity") {
            val url = testUrl("https://short.url/abc")
            coEvery { urlDao.update(urlToDb(url)) } returns Unit

            repository.updateURL(url)

            coVerify(exactly = 1) { urlDao.update(urlToDb(url)) }
        }

        should("updateURLs updates every mapped db entity") {
            val urls = listOf(testUrl("https://short.url/a"), testUrl("https://short.url/b"))
            coEvery { urlDao.updateMultiple(urls.map(::urlToDb)) } returns Unit

            repository.updateURLs(urls)

            coVerify(exactly = 1) { urlDao.updateMultiple(urls.map(::urlToDb)) }
        }

        should("deleteURL deletes by short url") {
            val url = testUrl("https://short.url/abc")
            coEvery { urlDao.delete("https://short.url/abc") } returns Unit

            repository.deleteURL(url)

            coVerify(exactly = 1) { urlDao.delete("https://short.url/abc") }
        }

        should("deleteURLs deletes every mapped db entity") {
            val urls = listOf(testUrl("https://short.url/a"), testUrl("https://short.url/b"))
            coEvery { urlDao.delete(urls.map(::urlToDb)) } returns Unit

            repository.deleteURLs(urls)

            coVerify(exactly = 1) { urlDao.delete(urls.map(::urlToDb)) }
        }
    },
)
