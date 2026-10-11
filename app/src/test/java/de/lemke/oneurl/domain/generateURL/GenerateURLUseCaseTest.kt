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

import com.android.volley.Request
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeNetworkMonitor
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.domain.CheckURLSafetyUseCase
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.UrlhausResult
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateURLUseCaseTest : ShouldSpec(
    {
        val networkMonitor = FakeNetworkMonitor()
        val checkURLSafety = mockk<CheckURLSafetyUseCase>()
        val queue = FakeVolleyQueue()
        val generateURL = GenerateURLUseCase(networkMonitor, checkURLSafety, queue, Dispatchers.Unconfined)

        beforeTest { networkMonitor.online = true }

        should("fail with NoInternet without validated internet") {
            networkMonitor.online = false
            val progress = mutableListOf<Int>()

            val result = generateURL(mockk<ShortURLProvider>(), "https://example.com", "alias", progress::add)

            result shouldBe GenerateURLResult.Failure(GenerateURLError.NoInternet)
            progress shouldContainExactly listOf(R.string.checking_internet)
        }

        should("fail with BlacklistedURL when the safety check reports a blacklisted url") {
            coEvery { checkURLSafety(any()) } returns
                UrlhausResult.Blacklisted("blacklisted", "urlhaus-link", "vt-link")

            val result = generateURL(mockk<ShortURLProvider>(), "https://example.com", "alias") {}

            result shouldBe
                GenerateURLResult.Failure(GenerateURLError.BlacklistedURL("blacklisted", "urlhaus-link", "vt-link"))
        }

        should("delegate to the provider and return Success when the safety check passes") {
            coEvery { checkURLSafety(any()) } returns UrlhausResult.Ok
            val provider =
                mockk<ShortURLProvider> {
                    every { getCreateRequest(queue, "https://example.com", "alias", any(), any()) } answers {
                        arg<(String) -> Unit>(3).invoke("https://short.url/xyz")
                        mockk<Request<Any>>(relaxed = true)
                    }
                }
            val progress = mutableListOf<Int>()

            val result = generateURL(provider, "example.com", "alias", progress::add)

            result shouldBe GenerateURLResult.Success("https://short.url/xyz")
            progress shouldContainExactly
                listOf(
                    R.string.checking_internet,
                    R.string.checking_url,
                    R.string.generating_url,
                )
        }

        should("return the provider's Failure when it reports an error") {
            coEvery { checkURLSafety(any()) } returns UrlhausResult.Ok
            val provider =
                mockk<ShortURLProvider> {
                    every { getCreateRequest(queue, any(), any(), any(), any()) } answers {
                        arg<(GenerateURLError) -> Unit>(4).invoke(GenerateURLError.RateLimitExceeded)
                        mockk<Request<Any>>(relaxed = true)
                    }
                }

            val result = generateURL(provider, "https://example.com", "alias") {}

            result shouldBe GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)
        }

        should("ignore a second success callback delivered after the continuation already resumed") {
            coEvery { checkURLSafety(any()) } returns UrlhausResult.Ok
            val provider =
                mockk<ShortURLProvider> {
                    every { getCreateRequest(queue, any(), any(), any(), any()) } answers {
                        arg<(String) -> Unit>(3).invoke("https://short.url/first")
                        arg<(String) -> Unit>(3).invoke("https://short.url/second")
                        mockk<Request<Any>>(relaxed = true)
                    }
                }

            val result = generateURL(provider, "https://example.com", "alias") {}

            result shouldBe GenerateURLResult.Success("https://short.url/first")
        }

        should("ignore a second failure callback delivered after the continuation already resumed") {
            coEvery { checkURLSafety(any()) } returns UrlhausResult.Ok
            val provider =
                mockk<ShortURLProvider> {
                    every { getCreateRequest(queue, any(), any(), any(), any()) } answers {
                        arg<(GenerateURLError) -> Unit>(4).invoke(GenerateURLError.RateLimitExceeded)
                        arg<(GenerateURLError) -> Unit>(4).invoke(GenerateURLError.NoInternet)
                        mockk<Request<Any>>(relaxed = true)
                    }
                }

            val result = generateURL(provider, "https://example.com", "alias") {}

            result shouldBe GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)
        }

        should("cancel the provider's request when the coroutine is cancelled") {
            coEvery { checkURLSafety(any()) } returns UrlhausResult.Ok
            val createdRequest = mockk<Request<Any>>(relaxed = true)
            val provider =
                mockk<ShortURLProvider> {
                    every { getCreateRequest(queue, any(), any(), any(), any()) } returns createdRequest
                }

            runTest {
                val job = launch { generateURL(provider, "https://example.com", "alias") {} }
                runCurrent()
                job.cancel()
                runCurrent()
            }

            verify { createdRequest.cancel() }
        }
    },
)
