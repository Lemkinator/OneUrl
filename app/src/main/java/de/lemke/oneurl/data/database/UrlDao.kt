package de.lemke.oneurl.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(url: UrlDb)

    @Query("SELECT * FROM url WHERE shortUrl = :shortUrl")
    suspend fun getUrl(shortUrl: String): UrlDb?

    @Query("SELECT * FROM url WHERE shortUrlProvider = :shortUrlProvider AND longUrl = :longUrl")
    suspend fun getUrl(shortUrlProvider: String, longUrl: String): UrlDb?

    @Query("SELECT * FROM url;")
    suspend fun getAll(): List<UrlDb>

    @Query("SELECT * FROM url")
    fun observeAll(): Flow<List<UrlDb>>

    @Update
    suspend fun update(url: UrlDb)

    @Update
    suspend fun updateMultiple(urls: List<UrlDb>)

    @Query("DELETE FROM url WHERE shortUrl = :shortUrl")
    suspend fun delete(shortUrl: String)

    @Query("DELETE FROM url;")
    suspend fun deleteAll()
}
