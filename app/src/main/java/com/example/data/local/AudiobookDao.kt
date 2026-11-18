package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AudiobookEntity>>

    @Query("SELECT * FROM audiobooks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AudiobookEntity?>

    @Query("SELECT * FROM audiobooks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AudiobookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AudiobookEntity)

    @Query("UPDATE audiobooks SET lastPositionMs = :positionMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, positionMs: Long, updatedAt: Long)

    @Query("DELETE FROM audiobooks WHERE id = :id")
    suspend fun deleteById(id: String)
}
