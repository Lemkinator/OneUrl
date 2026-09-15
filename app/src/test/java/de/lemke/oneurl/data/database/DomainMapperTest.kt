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

import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.Unknown
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DomainMapperTest : ShouldSpec(
    {
        val added = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)

        should("urlFromDb maps every field and resolves the provider by name") {
            val provider = ShortURLProviderCompanion.default
            val urlDb =
                URLDb(
                    shortURL = "https://short.url/abc",
                    longURL = "https://example.com",
                    shortURLProvider = provider.name,
                    favorite = true,
                    title = "title",
                    description = "description",
                    added = added,
                )

            val url = urlFromDb(urlDb)

            url.shortURL shouldBe urlDb.shortURL
            url.longURL shouldBe urlDb.longURL
            url.shortURLProvider shouldBe provider
            url.favorite shouldBe true
            url.title shouldBe "title"
            url.description shouldBe "description"
            url.added shouldBe added
        }

        should("urlFromDb falls back to Unknown for an unrecognized provider name") {
            val urlDb =
                URLDb(
                    shortURL = "https://short.url/abc",
                    longURL = "https://example.com",
                    shortURLProvider = "does-not-exist",
                    favorite = false,
                    title = "",
                    description = "",
                    added = added,
                )

            urlFromDb(urlDb).shortURLProvider.shouldBeInstanceOf<Unknown>()
        }

        should("urlToDb maps every field and stores the provider's name") {
            val provider = ShortURLProviderCompanion.default
            val url =
                de.lemke.oneurl.domain.model.URL(
                    shortURL = "https://short.url/abc",
                    longURL = "https://example.com",
                    shortURLProvider = provider,
                    favorite = true,
                    title = "title",
                    description = "description",
                    added = added,
                )

            val urlDb = urlToDb(url)

            urlDb.shortURL shouldBe url.shortURL
            urlDb.longURL shouldBe url.longURL
            urlDb.shortURLProvider shouldBe provider.name
            urlDb.favorite shouldBe true
            urlDb.title shouldBe "title"
            urlDb.description shouldBe "description"
            urlDb.added shouldBe added
        }

        should("urlToDb then urlFromDb round-trips to an equal URL for a known provider") {
            val provider = ShortURLProviderCompanion.default
            val url =
                de.lemke.oneurl.domain.model.URL(
                    shortURL = "https://short.url/abc",
                    longURL = "https://example.com",
                    shortURLProvider = provider,
                    favorite = false,
                    title = "title",
                    description = "description",
                    added = added,
                )

            urlFromDb(urlToDb(url)) shouldBe url
        }
    },
)
