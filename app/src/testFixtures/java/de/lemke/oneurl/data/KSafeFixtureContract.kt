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

import android.content.Context
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Test

abstract class KSafeFixtureContract {
    protected abstract val context: Context
    private val fixture = KSafeFixture { context }

    @After
    fun closeFixture() = runTest { fixture.close() }

    // KSafe creates the locked master key on its write scope after init and nothing joins it, so a put does not wait for it.
    // The poll runs on a real dispatcher: a delay inside runTest skips virtual time and lets no background work progress.
    private suspend fun awaitAliases(count: Int) =
        withContext(Dispatchers.Default) {
            withTimeout(POLL_TIMEOUT) {
                while (fixture.aliases().size < count) delay(POLL_INTERVAL)
            }
        }

    @Test
    fun closeDeletesTheKeystoreKeysOfTheStore() =
        runTest {
            fixture.openEmpty().put("key", "value", String.serializer())
            awaitAliases(2)
            fixture.aliases() shouldContainExactlyInAnyOrder
                listOf(
                    "eu.anifantakis.ksafe.${fixture.fileName}.__ksafe_master__",
                    "eu.anifantakis.ksafe.${fixture.fileName}.__ksafe_master_locked__",
                )

            fixture.close()

            fixture.aliases() shouldBe emptyList()
        }

    @Test
    fun closeDeletesTheKeystoreKeysOfAStoreThatTheTestClosedItself() =
        runTest {
            val store = fixture.openEmpty()
            store.put("key", "value", String.serializer())
            store.close()

            fixture.close()

            fixture.aliases() shouldBe emptyList()
        }

    private companion object {
        val POLL_TIMEOUT = 10.seconds
        val POLL_INTERVAL = 20.milliseconds
    }
}
