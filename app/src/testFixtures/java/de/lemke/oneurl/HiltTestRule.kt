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

package de.lemke.oneurl

import dagger.hilt.android.testing.HiltAndroidRule
import de.lemke.oneurl.data.database.AppDatabase
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Hilt discards the per-test component without closing the in-memory [AppDatabase] it holds. */
class HiltTestRule(
    testInstance: Any,
) : TestRule {
    private val hiltRule = HiltAndroidRule(testInstance)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement = RuleChain.outerRule(hiltRule).around { statement, _ -> closeResourcesAfter(statement) }.apply(base, description)

    fun inject() = hiltRule.inject()

    companion object {
        private val openResources = ConcurrentLinkedQueue<AutoCloseable>()
        private val registeredResources = AtomicInteger()

        /** How many resources were registered with [closeAfterTest] since the process started. */
        val registrations: Int get() = registeredResources.get()

        fun closeAfterTest(resource: AutoCloseable) {
            openResources.add(resource)
            registeredResources.incrementAndGet()
        }

        private fun closeResourcesAfter(base: Statement): Statement =
            object : Statement() {
                override fun evaluate() {
                    val testFailure = runCatching { base.evaluate() }.exceptionOrNull()
                    val closeFailure = closeOpenResources()
                    if (testFailure != null) {
                        closeFailure?.let(testFailure::addSuppressed)
                        throw testFailure
                    }
                    closeFailure?.let { throw it }
                }
            }

        private fun closeOpenResources(): Throwable? {
            var failure: Throwable? = null
            while (true) {
                val resource = openResources.poll() ?: break
                runCatching { resource.close() }.onFailure { error ->
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
            }
            return failure
        }
    }
}
