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

import de.lemke.oneurl.data.FakeTlyKeyStore
import de.lemke.oneurl.domain.model.TlyApiKey
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred

class ProviderInfoViewModelTest : ShouldSpec(
    {
        should("hold no key text until the stored key is read") {
            val store = FakeTlyKeyStore(TlyApiKey.ofOrNull("abc123"))
            store.gate = CompletableDeferred()
            val viewModel = ProviderInfoViewModel(store)

            viewModel.tlyKey.value shouldBe null

            store.gate?.complete(Unit)
            viewModel.tlyKey.value shouldBe "abc123"
        }

        should("show an empty text when no key is stored") {
            ProviderInfoViewModel(FakeTlyKeyStore()).tlyKey.value shouldBe ""
        }

        should("show the stored key") {
            ProviderInfoViewModel(FakeTlyKeyStore(TlyApiKey.ofOrNull("abc123"))).tlyKey.value shouldBe "abc123"
        }

        should("store a typed key and keep the text as typed") {
            val store = FakeTlyKeyStore()
            val viewModel = ProviderInfoViewModel(store)

            viewModel.onTlyKeyChanged(" abc123 ")

            viewModel.tlyKey.value shouldBe " abc123 "
            store.apiKey shouldBe TlyApiKey.ofOrNull("abc123")
        }

        should("remove the stored key when the text turns blank") {
            val store = FakeTlyKeyStore(TlyApiKey.ofOrNull("abc123"))
            val viewModel = ProviderInfoViewModel(store)

            viewModel.onTlyKeyChanged("")

            viewModel.tlyKey.value shouldBe ""
            store.apiKey shouldBe null
        }

        should("report a failed write until it is handled") {
            val store = FakeTlyKeyStore()
            store.failing = true
            val viewModel = ProviderInfoViewModel(store)
            viewModel.tlyKeySaveFailed.value shouldBe false

            viewModel.onTlyKeyChanged("abc123")

            viewModel.tlyKeySaveFailed.value shouldBe true
            viewModel.tlyKey.value shouldBe "abc123"

            viewModel.onTlyKeySaveFailedHandled()

            viewModel.tlyKeySaveFailed.value shouldBe false
        }

        should("show an empty text when the store cannot be read") {
            val store = FakeTlyKeyStore(TlyApiKey.ofOrNull("abc123"))
            store.failing = true

            ProviderInfoViewModel(store).tlyKey.value shouldBe ""
        }

        should("keep the last typed key when an earlier write finishes later") {
            val store = FakeTlyKeyStore()
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            store.setGates.addAll(listOf(first, second))
            val viewModel = ProviderInfoViewModel(store)

            viewModel.onTlyKeyChanged("abc")
            viewModel.onTlyKeyChanged("abcd")
            second.complete(Unit)
            store.apiKey shouldBe null

            first.complete(Unit)

            store.apiKey shouldBe TlyApiKey.ofOrNull("abcd")
        }

        should("write nothing when the text is the one already shown") {
            val store = FakeTlyKeyStore(TlyApiKey.ofOrNull("abc123"))
            val viewModel = ProviderInfoViewModel(store)
            store.apiKey = null

            viewModel.onTlyKeyChanged("abc123")

            store.apiKey shouldBe null
        }
    },
)
