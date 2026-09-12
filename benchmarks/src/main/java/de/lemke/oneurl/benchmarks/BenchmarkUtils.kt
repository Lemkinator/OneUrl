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

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "de.lemke.oneurl"
const val TIMEOUT_MS = 5_000L

// Must match de.lemke.commonutils.ui.utils.EXTRA_SKIP_ONBOARDING — cannot import from test module.
// Pinned by app/src/test/.../BenchmarkOnboardingKeyTest.kt.
const val EXTRA_SKIP_ONBOARDING = "commonUtilsSkipOnboarding"

fun MacrobenchmarkScope.startActivityAndSkipOnboarding() =
    startActivityAndWait { it.putExtra(EXTRA_SKIP_ONBOARDING, true) }

fun UiDevice.flingElementDownUp(element: UiObject2) {
    element.setGestureMargin(displayWidth / 5)
    element.fling(Direction.DOWN)
    waitForIdle()
    element.fling(Direction.UP)
}

fun UiDevice.waitAndFindObject(selector: BySelector, timeout: Long): UiObject2 =
    checkNotNull(wait(Until.findObject(selector), timeout)) {
        "Element not found on screen in ${timeout}ms (selector=$selector)"
    }
