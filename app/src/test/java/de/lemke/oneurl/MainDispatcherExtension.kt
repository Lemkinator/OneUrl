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

package de.lemke.oneurl

import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.isRootTest
import io.kotest.engine.test.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Spec bodies, beforeSpec and afterSpec run outside every TestCaseExtension, so Main is not set there. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension : TestCaseExtension {
    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult {
        if (!testCase.isRootTest()) return execute(testCase)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return try {
            execute(testCase)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
