package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_overrides")
data class MediaOverride(
    @PrimaryKey val mediaId: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val customTitle: String? = null,
    val customLocation: String? = null,
    val customDate: Long? = null,
    val customAlbum: String? = null,
    val rotation: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f
)
