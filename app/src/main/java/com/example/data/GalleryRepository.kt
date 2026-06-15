package com.example.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class GalleryRepository(
    private val context: Context,
    private val mediaOverrideDao: MediaOverrideDao,
    private val cachedMediaDao: CachedMediaDao
) {
    private fun hasReadPermission(): Boolean {
        val hasImages = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasVideos = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_VIDEO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasPartial = if (android.os.Build.VERSION.SDK_INT >= 34) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        return hasImages || hasVideos || hasPartial
    }

    @Suppress("DEPRECATION")
    private fun fetchDeviceMedia(): List<MediaItem>? {
        if (!hasReadPermission()) {
            Log.w("GalleryRepo", "Sync bypassed: Read media permissions are not granted yet.")
            return null
        }
        val items = mutableListOf<MediaItem>()

        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE
        )
        try {
            context.contentResolver.query(
                imageUri, imageProjection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val wIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val latIdx = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                val lonIdx = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val contentUri = ContentUris.withAppendedId(imageUri, id)
                    val name = cursor.getString(nameIdx) ?: continue
                    val dateAdded = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                    val width = if (wIdx != -1) cursor.getInt(wIdx) else 0
                    val height = if (hIdx != -1) cursor.getInt(hIdx) else 0
                    val location = readLocation(latIdx, lonIdx, cursor)

                    items.add(
                        MediaItem(
                            id = id.toString(),
                            uri = contentUri.toString(),
                            displayName = name,
                            dateAdded = dateAdded,
                            size = size,
                            width = width,
                            height = height,
                            location = location,
                            isVideo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error fetching images from MediaStore", e)
        }

        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.LATITUDE,
            MediaStore.Video.Media.LONGITUDE
        )
        try {
            context.contentResolver.query(
                videoUri, videoProjection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val wIdx = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val latIdx = cursor.getColumnIndex(MediaStore.Video.Media.LATITUDE)
                val lonIdx = cursor.getColumnIndex(MediaStore.Video.Media.LONGITUDE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val contentUri = ContentUris.withAppendedId(videoUri, id)
                    val name = cursor.getString(nameIdx) ?: continue
                    val dateAdded = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                    val width = if (wIdx != -1) cursor.getInt(wIdx) else 0
                    val height = if (hIdx != -1) cursor.getInt(hIdx) else 0
                    val duration = if (durIdx != -1) cursor.getLong(durIdx) else 0L
                    val location = readLocation(latIdx, lonIdx, cursor)

                    items.add(
                        MediaItem(
                            id = id.toString(),
                            uri = contentUri.toString(),
                            displayName = name,
                            dateAdded = dateAdded,
                            size = size,
                            width = width,
                            height = height,
                            duration = duration,
                            location = location,
                            isVideo = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryRepo", "Error fetching videos from MediaStore", e)
        }

        return items.sortedByDescending { it.dateAdded }
    }

    private fun readLocation(
        latIdx: Int, lonIdx: Int, cursor: android.database.Cursor
    ): String? {
        if (latIdx == -1 || lonIdx == -1) return null
        val lat = cursor.getDouble(latIdx)
        val lon = cursor.getDouble(lonIdx)
        if (lat == 0.0 && lon == 0.0) return null
        return String.format(Locale.US, "%.4f, %.4f", lat, lon)
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startSync(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val contentFlow = callbackFlow {
                val handler = Handler(Looper.getMainLooper())
                val observer = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
                try {
                    context.contentResolver.registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
                    )
                    context.contentResolver.registerContentObserver(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
                    )
                } catch (e: SecurityException) {
                    Log.e("GalleryRepo", "SecurityException registering content observers", e)
                }
                trySend(Unit)
                awaitClose {
                    try {
                        context.contentResolver.unregisterContentObserver(observer)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            contentFlow
                .debounce(100L)
                .collect {
                    try {
                        val deviceMedia = fetchDeviceMedia() ?: return@collect
                        val cached = deviceMedia.map {
                            CachedMediaItem(
                                id = it.id,
                                uri = it.uri,
                                displayName = it.displayName,
                                dateAdded = it.dateAdded,
                                duration = it.duration,
                                width = it.width,
                                height = it.height,
                                size = it.size,
                                location = it.location,
                                isVideo = it.isVideo
                            )
                        }
                        cachedMediaDao.syncMedia(cached)
                    } catch (e: Exception) {
                        Log.e("GalleryRepo", "Error syncing MediaStore with Room", e)
                    }
                }
        }
    }

    fun getMediaFlow(): Flow<List<MediaItem>> {
        return combine(cachedMediaDao.getAllCachedMedia(), mediaOverrideDao.getAllOverrides()) { dbItems, overrides ->
            val overridesMap = overrides.associateBy { it.mediaId }
            val mergedList = mutableListOf<MediaItem>()

            dbItems.forEach { dbItem ->
                val override = overridesMap[dbItem.id]
                mergedList.add(
                    MediaItem(
                        id = dbItem.id,
                        uri = dbItem.uri,
                        displayName = override?.customTitle ?: dbItem.displayName,
                        location = override?.customLocation ?: dbItem.location,
                        dateAdded = (override?.customDate ?: (dbItem.dateAdded * 1000)) / 1000,
                        duration = dbItem.duration,
                        width = dbItem.width,
                        height = dbItem.height,
                        size = dbItem.size,
                        isVideo = dbItem.isVideo,
                        isFavorite = override?.isFavorite ?: false,
                        isHidden = override?.isHidden ?: false,
                        customAlbum = override?.customAlbum,
                        rotation = override?.rotation ?: 0f,
                        brightness = override?.brightness ?: 0f,
                        contrast = override?.contrast ?: 1f,
                        saturation = override?.saturation ?: 1f
                    )
                )
            }

            val existingIds = dbItems.map { it.id }.toSet()
            overrides.forEach { override ->
                if (override.mediaId !in existingIds) {
                    val baseId = override.mediaId.substringBefore("_duplicate_")
                    val baseItem = dbItems.firstOrNull { it.id == baseId }
                    if (baseItem != null) {
                        mergedList.add(
                            MediaItem(
                                id = override.mediaId,
                                uri = baseItem.uri,
                                displayName = override.customTitle
                                    ?: ("Copia de " + baseItem.displayName),
                                location = override.customLocation ?: baseItem.location,
                                dateAdded = (override.customDate ?: System.currentTimeMillis()) / 1000,
                                duration = baseItem.duration,
                                width = baseItem.width,
                                height = baseItem.height,
                                size = baseItem.size,
                                isVideo = baseItem.isVideo,
                                isFavorite = override.isFavorite,
                                isHidden = override.isHidden,
                                customAlbum = override.customAlbum,
                                rotation = override.rotation,
                                brightness = override.brightness,
                                contrast = override.contrast,
                                saturation = override.saturation
                            )
                        )
                    }
                }
            }

            mergedList.distinctBy { it.id }.sortedByDescending { it.dateAdded }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun updateAdjustments(
        mediaId: String, rotation: Float, brightness: Float, contrast: Float, saturation: Float
    ) = withContext(Dispatchers.IO) {
        val existing = mediaOverrideDao.getOverrideById(mediaId)
        val updated = existing?.copy(
            rotation = rotation,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation
        ) ?: MediaOverride(
            mediaId = mediaId,
            rotation = rotation,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation
        )
        mediaOverrideDao.insertOverride(updated)
    }

    suspend fun toggleFavorite(mediaId: String, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        val existing = mediaOverrideDao.getOverrideById(mediaId)
        val updated = existing?.copy(isFavorite = !currentFavorite)
            ?: MediaOverride(mediaId = mediaId, isFavorite = !currentFavorite)
        mediaOverrideDao.insertOverride(updated)
    }

    suspend fun setHidden(mediaId: String, isHidden: Boolean) = withContext(Dispatchers.IO) {
        val existing = mediaOverrideDao.getOverrideById(mediaId)
        val updated = existing?.copy(isHidden = isHidden)
            ?: MediaOverride(mediaId = mediaId, isHidden = isHidden)
        mediaOverrideDao.insertOverride(updated)
    }

    suspend fun adjustMetadata(
        mediaId: String, newTitle: String?, newLocation: String?, newDateMs: Long?
    ) = withContext(Dispatchers.IO) {
        val existing = mediaOverrideDao.getOverrideById(mediaId)
        val updated = existing?.copy(
            customTitle = newTitle, customLocation = newLocation, customDate = newDateMs
        ) ?: MediaOverride(
            mediaId = mediaId,
            customTitle = newTitle,
            customLocation = newLocation,
            customDate = newDateMs
        )
        mediaOverrideDao.insertOverride(updated)
    }

    suspend fun updateCustomAlbum(mediaId: String, albumName: String?) = withContext(Dispatchers.IO) {
        val existing = mediaOverrideDao.getOverrideById(mediaId)
        val updated = existing?.copy(customAlbum = albumName)
            ?: MediaOverride(mediaId = mediaId, customAlbum = albumName)
        mediaOverrideDao.insertOverride(updated)
    }

    suspend fun createDuplicate(item: MediaItem) = withContext(Dispatchers.IO) {
        val duplicateId = "${item.id}_duplicate_${System.currentTimeMillis()}"
        val override = MediaOverride(
            mediaId = duplicateId,
            customTitle = "Duplicado - ${item.displayName}",
            customLocation = item.location,
            customDate = System.currentTimeMillis(),
            isFavorite = item.isFavorite,
            customAlbum = item.customAlbum
        )
        mediaOverrideDao.insertOverride(override)
    }
}
