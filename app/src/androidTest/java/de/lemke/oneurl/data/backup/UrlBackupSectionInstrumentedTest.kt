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

package de.lemke.oneurl.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.oneurl.data.database.AppDatabase
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class UrlBackupSectionInstrumentedTest : UrlBackupSectionContract() {
    private val database =
        Room
            .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .build()
    private val section = UrlBackupSection(database.urlDao())
    private val transaction = RoomBackupTransaction(database)
    private val originalZone = TimeZone.getDefault()

    @Before
    fun useBerlinZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun closeDatabaseAndRestoreZone() {
        database.close()
        TimeZone.setDefault(originalZone)
    }

    override fun section(): BackupSection<UrlBackupEntry> = section

    override fun transaction(): BackupTransaction = transaction
}
