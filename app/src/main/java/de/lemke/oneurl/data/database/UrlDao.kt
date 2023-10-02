package de.lemke.oneurl.data.database

import androidx.room.*
import de.lemke.oneurl.domain.model.ShortUrlProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(url: UrlDb)

    @Query("DELETE FROM url WHERE shortUrl = :shortUrl")
    suspend fun delete(shortUrl: String)

    @Query("SELECT * FROM url;")
    suspend fun getAll(): List<UrlDb>

    @Query("SELECT * FROM url WHERE shortUrlProvider = :shortUrlProvider AND longUrl = :longUrl")
    suspend fun getUrl(shortUrlProvider: ShortUrlProvider, longUrl: String): UrlDb?

    @Query("SELECT * FROM url")
    fun observeAll(): Flow<List<UrlDb>>

    @Query("DELETE FROM url;")
    suspend fun deleteAll()
}
