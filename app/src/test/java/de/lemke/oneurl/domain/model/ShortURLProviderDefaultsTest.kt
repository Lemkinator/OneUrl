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

import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.domain.testUrl
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ShortURLProviderDefaultsTest : ShouldSpec(
    {
        val provider = MinimalProvider()

        should("enabled defaults to true") {
            provider.enabled.shouldBeTrue()
        }

        should("group defaults to name") {
            provider.group shouldBe provider.name
        }

        should("apiURL defaults to baseURL") {
            provider.apiURL shouldBe provider.baseURL
        }

        should("infoURL defaults to baseURL") {
            provider.infoURL shouldBe provider.baseURL
        }

        should("privacyURL defaults to null") {
            provider.privacyURL.shouldBeNull()
        }

        should("termsURL defaults to null") {
            provider.termsURL.shouldBeNull()
        }

        should("aliasConfig defaults to null") {
            provider.aliasConfig.shouldBeNull()
        }

        should("getAnalyticsURL defaults to null") {
            provider.getAnalyticsURL("alias").shouldBeNull()
        }

        should("getURLClickCount defaults to reporting null clicks") {
            var clicks: Int? = -1
            provider.getURLClickCount(FakeVolleyQueue(), testUrl("https://minimal.example/abc"), { clicks = it })

            clicks.shouldBeNull()
        }

        should("sanitizeLongURL defaults to trimming the URL") {
            provider.sanitizeLongURL("  https://example.com  ") shouldBe "https://example.com"
        }
    },
)
