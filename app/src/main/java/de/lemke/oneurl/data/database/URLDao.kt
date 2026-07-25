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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface URLDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(url: URLDb)

    @Query("SELECT * FROM url WHERE shortURL = :shortURL")
    suspend fun getURL(shortURL: String): URLDb?

    @Query("SELECT * FROM url WHERE shortURLProvider = :shortURLProvider AND longURL = :longURL")
    suspend fun getURL(
        shortURLProvider: String,
        longURL: String,
    ): List<URLDb>

    @Query("SELECT * FROM url;")
    suspend fun getAll(): List<URLDb>

    @Query("SELECT * FROM url")
    fun observeAll(): Flow<List<URLDb>>

    @Update
    suspend fun update(url: URLDb)

    @Update
    suspend fun updateMultiple(urls: List<URLDb>)

    @Query("DELETE FROM url WHERE shortURL = :shortURL")
    suspend fun delete(shortURL: String)

    @Transaction
    suspend fun delete(urls: List<URLDb>) = urls.forEach { delete(it.shortURL) }

    @Query("DELETE FROM url;")
    suspend fun deleteAll()
}
