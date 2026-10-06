package com.ryan.tdee.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double?,
    val calories: Double?,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY epochDay")
    fun observeAll(): Flow<List<EntryEntity>>

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("DELETE FROM entries")
    suspend fun clear()
}

@Database(entities = [EntryEntity::class], version = 1, exportSchema = false)
abstract class EntryDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        fun build(context: Context): EntryDatabase =
            Room.databaseBuilder(context, EntryDatabase::class.java, "tdee.db").build()
    }
}
