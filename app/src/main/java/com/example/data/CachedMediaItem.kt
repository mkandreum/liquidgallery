package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_media_items")
data class CachedMediaItem(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val dateAdded: Long,
    val duration: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val location: String? = null,
    val isVideo: Boolean = false
)
