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
import java.io.File

/**
 * `:benchmarks` is a `com.android.test` module and cannot depend on common-utils (see the SESL
 * exclusion comment in the root build.gradle.kts), so `BenchmarkUtils.kt` hardcodes this key
 * instead of importing it. Reads that file's actual declaration - not a second hardcoded literal -
 * so either a rename in common-utils or independent drift in the benchmark copy reddens this test
 * instead of silently breaking benchmark generation at runtime.
 */
class BenchmarkOnboardingKeyTest : ShouldSpec(
    {
        should("match the actual literal declared in benchmarks/src/main/.../BenchmarkUtils.kt") {
            val benchmarkUtils = File("../benchmarks/src/main/java/de/lemke/oneurl/benchmarks/BenchmarkUtils.kt")
            check(benchmarkUtils.isFile) { "BenchmarkUtils.kt not found at ${benchmarkUtils.absolutePath}" }
            val declared =
                Regex("""const val EXTRA_SKIP_ONBOARDING = "([^"]+)"""")
                    .find(benchmarkUtils.readText())
                    ?.groupValues
                    ?.get(1)
            checkNotNull(declared) { "EXTRA_SKIP_ONBOARDING declaration not found in BenchmarkUtils.kt" }
            declared shouldBe EXTRA_SKIP_ONBOARDING
        }
    },
)
