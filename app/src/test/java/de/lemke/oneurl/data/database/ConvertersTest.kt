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

package de.lemke.oneurl.data.database

import android.app.Application
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// android.util.Log touches a static native binding under the default unit-test "not mocked" stub
// jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ConvertersTest {
    @Test
    fun `zonedDateTimeToDb returns the ISO string representation`() {
        val dateTime = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)

        Converters.zonedDateTimeToDb(dateTime) shouldBe dateTime.toString()
    }

    @Test
    fun `zonedDateTimeFromDb parses a valid ISO string back to the same instant`() {
        val dateTime = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)

        Converters.zonedDateTimeFromDb(dateTime.toString()) shouldBe dateTime
    }

    @Test
    fun `zonedDateTimeFromDb returns null for a null string`() {
        Converters.zonedDateTimeFromDb(null) shouldBe null
    }

    @Test
    fun `zonedDateTimeFromDb returns null for an unparseable string`() {
        Converters.zonedDateTimeFromDb("not a date") shouldBe null
    }
}
