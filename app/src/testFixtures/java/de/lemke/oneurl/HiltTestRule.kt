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
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The only rule a `@HiltAndroidTest` declares. Runs [HiltAndroidRule] for [testInstance] and, inside it, closes every
 * [AppDatabase] that [TestPersistenceModule] built during the test. A close failure never replaces a test failure:
 * it is added to the test failure as suppressed.
 */
class HiltTestRule(
    testInstance: Any,
) : TestRule {
    private val hiltRule = HiltAndroidRule(testInstance)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement = RuleChain.outerRule(hiltRule).around { statement, _ -> closeDatabasesAfter(statement) }.apply(base, description)

    fun inject() = hiltRule.inject()

    companion object {
        private val openDatabases = ConcurrentLinkedQueue<AppDatabase>()

        /** Registers [database] to be closed when the running test finishes. */
        fun closeAfterTest(database: AppDatabase): AppDatabase = database.also(openDatabases::add)

        private fun closeDatabasesAfter(base: Statement): Statement =
            object : Statement() {
                override fun evaluate() {
                    val testFailure = runCatching { base.evaluate() }.exceptionOrNull()
                    val closeFailure = closeOpenDatabases()
                    if (testFailure != null) {
                        closeFailure?.let(testFailure::addSuppressed)
                        throw testFailure
                    }
                    closeFailure?.let { throw it }
                }
            }

        private fun closeOpenDatabases(): Throwable? {
            var failure: Throwable? = null
            while (true) {
                val database = openDatabases.poll() ?: break
                runCatching { database.close() }.onFailure { error ->
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
            }
            return failure
        }
    }
}
