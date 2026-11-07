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

package de.lemke.oneurl.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.oneurl.R
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun provider_select() = capture(ProviderScreenMode.Select, "provider_select")

    @Test
    @Config(qualifiers = "+night")
    fun provider_select_dark() = capture(ProviderScreenMode.Select, "provider_select_dark")

    @Test
    fun provider_info() = capture(ProviderScreenMode.Info, "provider_info")

    @Test
    @Config(qualifiers = "+night")
    fun provider_info_dark() = capture(ProviderScreenMode.Info, "provider_info_dark")

    @Test
    @Config(qualifiers = "w320dp-h1400dp")
    fun provider_select_large_font() {
        RuntimeEnvironment.setFontScale(2f)
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.provider_list)
                val rows = (0 until recycler.childCount).map { recycler.getChildAt(it) }
                rows.forEach { row ->
                    val title = row.findViewById<TextView>(R.id.providerTitle)
                    val icons = row.findViewById<View>(R.id.providerIconLayout)
                    icons.right shouldBe row.width
                    icons.width shouldBeGreaterThan 0
                    title.right shouldBeLessThanOrEqual icons.left
                }
                rows.any { it.findViewById<TextView>(R.id.providerTitle).layout.getEllipsisCount(0) > 0 }.shouldBeTrue()
            }
            onView(isRoot()).captureRoboImage("src/test/screenshots/provider_select_large_font.png")
        }
    }

    private fun capture(
        mode: ProviderScreenMode,
        name: String,
    ) {
        launchProviderActivity(mode).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
        }
    }
}
