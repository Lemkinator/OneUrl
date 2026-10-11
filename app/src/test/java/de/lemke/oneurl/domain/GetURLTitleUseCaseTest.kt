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

package de.lemke.oneurl.domain

import de.lemke.oneurl.data.FakePageTitleFetcher
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class GetURLTitleUseCaseTest : ShouldSpec(
    {
        should("fetch the title of the url with an https scheme") {
            val fetcher = FakePageTitleFetcher().apply { titles["https://example.com"] = "Example" }

            GetURLTitleUseCase(fetcher)("example.com") shouldBe "Example"

            fetcher.requested shouldBe listOf("https://example.com")
        }
    },
)
