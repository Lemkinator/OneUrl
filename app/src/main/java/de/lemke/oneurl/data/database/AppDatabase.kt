package de.lemke.oneurl.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 1,
    entities = [
        URLDb::class,
    ],
    exportSchema = true,
    autoMigrations = [],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun urlDao(): URLDao
}
