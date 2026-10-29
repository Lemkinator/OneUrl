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

import de.lemke.oneurl.domain.model.TlyApiKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Behavior every [TlyKeyStore] must show. */
abstract class TlyKeyStoreContract {
    protected abstract fun createStore(): TlyKeyStore

    @Test
    fun getReturnsNullBeforeAKeyWasStored() =
        runTest {
            createStore().get() shouldBe null
        }

    @Test
    fun getReturnsTheStoredKey() =
        runTest {
            val store = createStore()

            store.set(TlyApiKey.ofOrNull("abc123"))

            store.get() shouldBe TlyApiKey.ofOrNull("abc123")
        }

    @Test
    fun setReplacesTheStoredKey() =
        runTest {
            val store = createStore()
            store.set(TlyApiKey.ofOrNull("first"))

            store.set(TlyApiKey.ofOrNull("second"))

            store.get() shouldBe TlyApiKey.ofOrNull("second")
        }

    @Test
    fun setNullRemovesTheStoredKey() =
        runTest {
            val store = createStore()
            store.set(TlyApiKey.ofOrNull("abc123"))

            store.set(null)

            store.get() shouldBe null
        }
}
