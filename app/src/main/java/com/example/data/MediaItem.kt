package com.example.data

import android.net.Uri

data class MediaItem(
    val id: String,
    val uri: String,
    val displayName: String,
    val dateAdded: Long,
    val duration: Long? = null, // in milliseconds, if video
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val location: String? = null,
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val customAlbum: String? = null
) {
    val durationString: String?
        get() {
            if (!isVideo || duration == null) return null
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
