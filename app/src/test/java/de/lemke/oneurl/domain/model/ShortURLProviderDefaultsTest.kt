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

import android.content.Context
import com.android.volley.Request
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

private class MinimalProvider : ShortURLProvider {
    override val name = "Minimal"
    override val baseURL = "https://minimal.example"

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): Request<*> = throw UnsupportedOperationException()
}

private class ProviderWithLinks : ShortURLProvider {
    override val name = "WithLinks"
    override val baseURL = "https://withlinks.example"
    override val privacyURL = "https://withlinks.example/privacy"
    override val termsURL = "https://withlinks.example/terms"

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): Request<*> = throw UnsupportedOperationException()
}

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
            provider.getURLClickCount(mockk(), mockk(), { clicks = it })

            clicks.shouldBeNull()
        }

        should("sanitizeLongURL defaults to trimming the URL") {
            provider.sanitizeLongURL("  https://example.com  ") shouldBe "https://example.com"
        }

        should("getInfoContents defaults to an empty list") {
            provider.getInfoContents(mockk()) shouldBe emptyList()
        }

        should("getTipsCardTitleAndInfo defaults to null") {
            provider.getTipsCardTitleAndInfo(mockk()).shouldBeNull()
        }

        should("getInfoButtons defaults to only the info entry when privacy and terms are absent") {
            val context = mockk<Context>()
            every { context.getString(any()) } returns "More information"

            val buttons = provider.getInfoButtons(context)

            buttons.size shouldBe 1
            buttons[0].linkOrDescription shouldBe provider.infoURL
        }

        should("getInfoButtons includes privacy and terms entries when present") {
            val withLinks = ProviderWithLinks()
            val context = mockk<Context>()
            every { context.getString(any()) } returns "label"

            val buttons = withLinks.getInfoButtons(context)

            buttons.size shouldBe 3
            buttons[0].linkOrDescription shouldBe withLinks.privacyURL
            buttons[1].linkOrDescription shouldBe withLinks.termsURL
            buttons[2].linkOrDescription shouldBe withLinks.infoURL
        }
    },
)
