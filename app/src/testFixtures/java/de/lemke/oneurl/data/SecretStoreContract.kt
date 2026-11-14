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

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

abstract class SecretStoreContract {
    protected abstract fun createStore(): SecretStore

    @Test
    fun getReturnsNullBeforeAnyPut() =
        runTest {
            createStore().get("first") shouldBe null
        }

    @Test
    fun putReplacesTheValueOfItsKey() =
        runTest {
            val store = createStore()
            store.put("first", "one")

            store.put("first", "two")

            store.get("first") shouldBe "two"
        }

    @Test
    fun keysDoNotSeeEachOthersValues() =
        runTest {
            val store = createStore()

            store.put("first", "one")
            store.put("second", "two")

            store.get("first") shouldBe "one"
            store.get("second") shouldBe "two"
            store.get("third") shouldBe null
        }

    @Test
    fun getReturnsNullAfterDeleteAndKeepsOtherKeys() =
        runTest {
            val store = createStore()
            store.put("first", "one")
            store.put("second", "two")

            store.delete("first")

            store.get("first") shouldBe null
            store.get("second") shouldBe "two"
        }
}
