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

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.data.database.AppDatabase
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class HiltTestRuleTest {
    @get:Rule(order = 0)
    val outcome = OutcomeRule()

    @get:Rule(order = 1)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var database: AppDatabase

    @Test
    fun `closes the injected database after the test`() {
        hiltRule.inject()
        database.openHelper.writableDatabase
        database.isOpen shouldBe true
        outcome.verify = { failure ->
            failure.shouldBeNull()
            database.isOpen shouldBe false
        }
    }

    @Test
    fun `keeps the test failure when closing a database also fails`() {
        val testFailure = AssertionError("test failed")
        val closeFailure = IllegalStateException("close failed")
        hiltRule.inject()
        database.openHelper.writableDatabase
        HiltTestRule.closeAfterTest(mockk { every { close() } throws closeFailure })
        outcome.verify = { failure ->
            failure shouldBeSameInstanceAs testFailure
            failure!!.suppressed.toList() shouldContainExactly listOf(closeFailure)
            database.isOpen shouldBe false
        }
        throw testFailure
    }

    @Test
    fun `reports a close failure when the test passes`() {
        val closeFailure = IllegalStateException("close failed")
        HiltTestRule.closeAfterTest(mockk { every { close() } throws closeFailure })
        outcome.verify = { failure -> failure shouldBeSameInstanceAs closeFailure }
    }

    class OutcomeRule : TestRule {
        var verify: (Throwable?) -> Unit = { failure -> failure?.let { throw it } }

        override fun apply(
            base: Statement,
            description: Description,
        ) = object : Statement() {
            override fun evaluate() {
                val failure = runCatching { base.evaluate() }.exceptionOrNull()
                verify(failure)
            }
        }
    }
}
