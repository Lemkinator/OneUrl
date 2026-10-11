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

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.oneuiproject.oneui.R as oneuiR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShortURLProviderInfoDefaultsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `getInfoContents defaults to an empty list`() {
        MinimalProvider().getInfoContents(context).shouldBeEmpty()
    }

    @Test
    fun `getTipsCardTitleAndInfo defaults to null`() {
        MinimalProvider().getTipsCardTitleAndInfo(context).shouldBeNull()
    }

    @Test
    fun `getInfoButtons lists only the info entry when privacy and terms are absent`() {
        val buttons = MinimalProvider().getInfoButtons(context).map { Triple(it.icon, it.title, it.linkOrDescription) }

        buttons shouldBe
            listOf(
                Triple(oneuiR.drawable.ic_oui_info_outline, "More Information", "https://minimal.example"),
            )
    }

    @Test
    fun `getInfoButtons lists privacy, terms and info entries when privacy and terms are present`() {
        val buttons = ProviderWithLinks().getInfoButtons(context).map { Triple(it.icon, it.title, it.linkOrDescription) }

        buttons shouldBe
            listOf(
                Triple(oneuiR.drawable.ic_oui_privacy, "Privacy Policy", "https://withlinks.example/privacy"),
                Triple(oneuiR.drawable.ic_oui_memo_outline, "Terms of Service", "https://withlinks.example/terms"),
                Triple(oneuiR.drawable.ic_oui_info_outline, "More Information", "https://withlinks.example"),
            )
    }
}
