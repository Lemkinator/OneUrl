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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ShortURLProviderCompanionTest : ShouldSpec(
    {
        should("enabled only contains providers whose enabled flag is true") {
            ShortURLProviderCompanion.enabled.all { it.enabled }.shouldBeTrue()
            ShortURLProviderCompanion.enabled.size shouldBe ShortURLProviderCompanion.all.count { it.enabled }
        }

        should("all contains at least one disabled provider (fixture assumption for other tests)") {
            ShortURLProviderCompanion.all.any { !it.enabled }.shouldBeTrue()
        }

        should("default is the first enabled provider") {
            ShortURLProviderCompanion.default shouldBe ShortURLProviderCompanion.enabled.first()
            ShortURLProviderCompanion.default.enabled.shouldBeTrue()
        }

        should("getIfEnabledOrDefault returns the given provider when it is enabled") {
            val enabledProvider = ShortURLProviderCompanion.enabled.last()

            ShortURLProviderCompanion.getIfEnabledOrDefault(enabledProvider) shouldBe enabledProvider
        }

        should("getIfEnabledOrDefault returns default when the given provider is disabled") {
            val disabledProvider = ShortURLProviderCompanion.all.first { !it.enabled }

            ShortURLProviderCompanion.getIfEnabledOrDefault(disabledProvider) shouldBe ShortURLProviderCompanion.default
        }

        should("getIfEnabledOrDefault returns default when the given provider is null") {
            ShortURLProviderCompanion.getIfEnabledOrDefault(null) shouldBe ShortURLProviderCompanion.default
        }

        should("fromString returns the matching provider by name") {
            val provider = ShortURLProviderCompanion.default

            ShortURLProviderCompanion.fromString(provider.name) shouldBe provider
        }

        should("fromString returns an Unknown provider for an unrecognized name") {
            val result = ShortURLProviderCompanion.fromString("does-not-exist")

            result.shouldBeInstanceOf<Unknown>()
            result.enabled.shouldBeFalse()
        }

        should("fromStringOrDefault returns the matching provider by name") {
            val provider = ShortURLProviderCompanion.default

            ShortURLProviderCompanion.fromStringOrDefault(provider.name) shouldBe provider
        }

        should("fromStringOrDefault returns default for an unrecognized name") {
            ShortURLProviderCompanion.fromStringOrDefault("does-not-exist") shouldBe ShortURLProviderCompanion.default
        }

        should("fromStringOrDefault returns default for a null name") {
            ShortURLProviderCompanion.fromStringOrDefault(null) shouldBe ShortURLProviderCompanion.default
        }
    },
)
