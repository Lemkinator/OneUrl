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

package de.lemke.oneurl.data

import android.app.Application
import de.lemke.oneurl.domain.model.TlyApiKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultTlyKeyStoreTest : TlyKeyStoreContract() {
    private val secrets = FakeSecretStore()

    override fun createStore(): TlyKeyStore = DefaultTlyKeyStore(secrets)

    @Test
    fun `get returns null for a blank stored text`() =
        runTest {
            secrets.put("tlyApiKey", "  ")

            createStore().get() shouldBe null
        }

    @Test
    fun `set stores the token under the key name`() =
        runTest {
            createStore().set(TlyApiKey.ofOrNull("abc123"))

            secrets.stored("tlyApiKey") shouldBe "abc123"
        }

    @Test
    fun `get returns null when the secret store fails`() =
        runTest {
            secrets.put("tlyApiKey", "abc123")
            secrets.failure = IllegalStateException("keystore unavailable")

            createStore().get() shouldBe null
        }

    @Test
    fun `set leaves the stored key when the secret store fails`() =
        runTest {
            secrets.put("tlyApiKey", "abc123")
            secrets.failure = IllegalStateException("keystore unavailable")

            createStore().set(TlyApiKey.ofOrNull("other"))
            createStore().set(null)

            secrets.failure = null
            secrets.stored("tlyApiKey") shouldBe "abc123"
        }
}
