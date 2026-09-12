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

package de.lemke.oneurl.benchmarks

import de.lemke.commonutils.ui.utils.EXTRA_SKIP_ONBOARDING
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

/**
 * `:benchmarks` is a `com.android.test` module and cannot depend on common-utils (see the SESL
 * exclusion comment in the root build.gradle.kts), so `BenchmarkUtils.kt` hardcodes this key
 * instead of importing it. This pins that copy against the real constant, so a rename in
 * common-utils reddens this test instead of silently breaking benchmark generation at runtime.
 */
class BenchmarkOnboardingKeyTest : ShouldSpec(
    {
        should("match the literal key hardcoded in benchmarks/src/main/.../BenchmarkUtils.kt") {
            EXTRA_SKIP_ONBOARDING shouldBe "commonUtilsSkipOnboarding"
        }
    },
)
