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

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The url list is empty on a fresh install (unlike an always-populated system list),
// so the drawer's fixed navigation menu is the only content guaranteed scrollable.
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial())

    private fun scroll(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 10,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndSkipOnboarding()
                device.swipe(5, device.displayHeight / 2, device.displayWidth / 2, device.displayHeight / 2, 15)
                device.waitAndFindObject(
                    By.res(PACKAGE_NAME, "navigationView").hasDescendant(By.clazz("android.widget.TextView")),
                    TIMEOUT_MS,
                )
            },
        ) {
            val nav = checkNotNull(device.findObject(By.res(PACKAGE_NAME, "navigationView"))) { "navigationView not found" }
            device.flingElementDownUp(nav)
        }
}
