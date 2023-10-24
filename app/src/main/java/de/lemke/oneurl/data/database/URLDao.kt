package de.lemke.oneurl.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface URLDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(url: URLDb)

    @Query("SELECT * FROM url WHERE shortURL = :shortURL")
    suspend fun getURL(shortURL: String): URLDb?

    @Query("SELECT * FROM url WHERE shortURLProvider = :shortURLProvider AND longURL = :longURL")
    suspend fun getURL(shortURLProvider: String, longURL: String): List<URLDb>

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
