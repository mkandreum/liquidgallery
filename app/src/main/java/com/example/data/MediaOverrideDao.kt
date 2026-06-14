package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaOverrideDao {
    @Query("SELECT * FROM media_overrides")
    fun getAllOverrides(): Flow<List<MediaOverride>>

    @Query("SELECT * FROM media_overrides WHERE mediaId = :mediaId")
    suspend fun getOverrideById(mediaId: String): MediaOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: MediaOverride)

    @Query("DELETE FROM media_overrides WHERE mediaId = :mediaId")
    suspend fun deleteOverride(mediaId: String)
}
