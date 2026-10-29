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
import de.lemke.oneurl.data.FakeVolleyQueue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TwtrtoTest {
    @Test
    fun `stored twtr_to links keep resolving to their provider`() {
        ShortURLProviderCompanion.fromString("t.ly (twtr.to)") shouldBe Twtrto
    }

    @Test
    fun `a stored twtr_to name falls back to the default provider`() {
        ShortURLProviderCompanion.fromStringOrDefault("t.ly (twtr.to)") shouldBe ShortURLProviderCompanion.default
    }

    @Test
    fun `keeps the T_LY group and legal links`() {
        Twtrto.group shouldBe "t.ly [experimental]"
        Twtrto.privacyURL shouldBe "https://t.ly/privacy"
        Twtrto.termsURL shouldBe "https://t.ly/terms"
    }

    @Test
    fun `is no longer offered`() {
        Twtrto.enabled.shouldBeFalse()
        (Twtrto in ShortURLProviderCompanion.enabled).shouldBeFalse()
    }

    @Test
    fun `cannot create a new link`() {
        var error: GenerateURLError? = null

        Twtrto.getCreateRequest(FakeVolleyQueue(), "https://example.com", "", { fail("unexpected success") }, { error = it })

        error shouldBe GenerateURLError.Unknown()
    }
}
