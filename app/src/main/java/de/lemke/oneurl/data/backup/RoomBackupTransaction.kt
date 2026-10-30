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

import androidx.room.withTransaction
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.oneurl.data.database.AppDatabase
import javax.inject.Inject

class RoomBackupTransaction @Inject constructor(
    private val database: AppDatabase,
) : BackupTransaction {
    override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction { block() }
}
