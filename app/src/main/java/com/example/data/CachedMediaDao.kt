package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedMediaDao {
    @Query("SELECT * FROM cached_media_items ORDER BY dateAdded DESC")
    fun getAllCachedMedia(): Flow<List<CachedMediaItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedMediaItem>)

    @Query("DELETE FROM cached_media_items WHERE id NOT IN (:ids)")
    suspend fun deleteOldItems(ids: List<String>)

    @Transaction
    suspend fun syncMedia(items: List<CachedMediaItem>) {
        insertAll(items)
        if (items.isNotEmpty()) {
            deleteOldItems(items.map { it.id })
        } else {
            clearAll()
        }
    }

    @Query("DELETE FROM cached_media_items")
    suspend fun clearAll()
}
