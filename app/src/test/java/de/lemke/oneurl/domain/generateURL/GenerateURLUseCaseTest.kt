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

package de.lemke.oneurl.domain.generateURL

import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeNetworkMonitor
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.FakeShortURLClient.Create
import de.lemke.oneurl.data.FakeUrlhausClient
import de.lemke.oneurl.domain.CheckURLSafetyUseCase
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.UrlhausResult
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers

class GenerateURLUseCaseTest : ShouldSpec(
    {
        lateinit var networkMonitor: FakeNetworkMonitor
        lateinit var urlhausClient: FakeUrlhausClient
        lateinit var shortURLClient: FakeShortURLClient
        lateinit var generateURL: GenerateURLUseCase

        beforeTest {
            networkMonitor = FakeNetworkMonitor()
            urlhausClient = FakeUrlhausClient()
            shortURLClient = FakeShortURLClient()
            generateURL = GenerateURLUseCase(networkMonitor, CheckURLSafetyUseCase(urlhausClient), shortURLClient, Dispatchers.Unconfined)
        }

        should("fail with NoInternet without validated internet and check nothing else") {
            networkMonitor.online = false
            val progress = mutableListOf<Int>()

            val result = generateURL(Dagd, "https://example.com", "alias", progress::add)

            result shouldBe GenerateURLResult.Failure(GenerateURLError.NoInternet)
            progress shouldBe listOf(R.string.checking_internet)
            urlhausClient.checked shouldBe emptyList()
            shortURLClient.created shouldBe emptyList()
        }

        should("fail with BlacklistedURL when URLhaus lists the url") {
            urlhausClient.results["https://example.com"] = UrlhausResult.Blacklisted("blacklisted", "urlhaus-link", "vt-link")
            val progress = mutableListOf<Int>()

            val result = generateURL(Dagd, "https://example.com", "alias", progress::add)

            result shouldBe GenerateURLResult.Failure(GenerateURLError.BlacklistedURL("blacklisted", "urlhaus-link", "vt-link"))
            progress shouldBe listOf(R.string.checking_internet, R.string.checking_url)
            shortURLClient.created shouldBe emptyList()
        }

        should("check and shorten the url with an https scheme and report each step") {
            shortURLClient.results[Dagd] = GenerateURLResult.Success("https://da.gd/xyz")
            val progress = mutableListOf<Int>()

            val result = generateURL(Dagd, "example.com", "alias", progress::add)

            result shouldBe GenerateURLResult.Success("https://da.gd/xyz")
            progress shouldBe listOf(R.string.checking_internet, R.string.checking_url, R.string.generating_url)
            urlhausClient.checked shouldBe listOf("https://example.com")
            shortURLClient.created shouldBe listOf(Create(Dagd, "https://example.com", "alias"))
        }

        should("return the provider's Failure") {
            shortURLClient.results[Dagd] = GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)

            val result = generateURL(Dagd, "https://example.com", "alias") {}

            result shouldBe GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)
        }
    },
)
