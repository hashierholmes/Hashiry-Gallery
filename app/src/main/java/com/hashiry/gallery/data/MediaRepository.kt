package com.hashiry.gallery.data

import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.provider.MediaStore
import com.hashiry.gallery.model.DeviceAlbum
import com.hashiry.gallery.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's media index (MediaStore) — no network, no accounts.
 */
class MediaRepository(private val context: Context) {

    fun observeMediaChanges(onChanged: (Uri?) -> Unit): AutoCloseable {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChanged(uri)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        return AutoCloseable { resolver.unregisterContentObserver(observer) }
    }

    suspend fun loadLibrary(): List<MediaItem> = withContext(Dispatchers.IO) {
        val all = ArrayList<MediaItem>(1024)
        runCatching { all.addAll(queryImages()) }
        runCatching { all.addAll(queryVideos()) }
        runCatching { all.addAll(queryPsdFiles()) }
        all.sortWith { a, b ->
            val dateCmp = b.dateMillis.compareTo(a.dateMillis)
            if (dateCmp != 0) dateCmp else b.id.compareTo(a.id)
        }
        all
    }

    private fun queryImages(): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val result = ArrayList<MediaItem>(1024)
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
                // Some Android media providers index PSDs in Images as well as Files.
                // PSDs are intentionally handled only by queryPsdFiles() below.
                if (displayName.endsWith(".psd", true) || mimeType.contains("photoshop", true)) continue
                val taken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                val added = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                result.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Photo" else "Photo",
                        mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/*" else "image/*",
                        isVideo = false,
                        dateMillis = if (taken > 0) taken else added * 1000L,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                        bucketId = if (bucketIdCol >= 0) cursor.getLong(bucketIdCol) else 0L,
                        bucketName = (if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null)
                            ?: "Internal storage",
                        durationMs = 0L
                    )
                )
            }
        }
        return result
    }

    private fun queryVideos(): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        val result = ArrayList<MediaItem>(128)
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                val taken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                val added = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                result.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Video" else "Video",
                        mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/*" else "video/*",
                        isVideo = true,
                        dateMillis = if (taken > 0) taken else added * 1000L,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                        bucketId = if (bucketIdCol >= 0) cursor.getLong(bucketIdCol) else 0L,
                        bucketName = (if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null)
                            ?: "Internal storage",
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    )
                )
            }
        }
        return result
    }

    private fun queryPsdFiles(): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT, MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
        )
        val result = ArrayList<MediaItem>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("%.psd"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                if (!name.endsWith(".psd", true)) continue
                result.add(
                    MediaItem(
                        id = -id.coerceAtLeast(1L),
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name,
                        mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/vnd.adobe.photoshop" else "image/vnd.adobe.photoshop",
                        isVideo = false,
                        dateMillis = (if (dateCol >= 0) cursor.getLong(dateCol) else 0L) * 1000L,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                        bucketId = if (bucketIdCol >= 0) cursor.getLong(bucketIdCol) else 0L,
                        bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "Internal storage" else "Internal storage",
                        durationMs = 0L
                    )
                )
            }
        }
        return result.distinctBy { item ->
            "${item.name.lowercase()}|${item.sizeBytes}|${item.dateMillis}|${item.bucketId}"
        }
    }

    fun buildDeviceAlbums(items: List<MediaItem>): List<DeviceAlbum> {
        val grouped = LinkedHashMap<Long, MutableList<MediaItem>>()
        for (item in items) {
            grouped.getOrPut(item.bucketId) { mutableListOf() }.add(item)
        }
        return grouped.map { (bucketId, list) ->
            DeviceAlbum(
                bucketId = bucketId,
                name = list.firstOrNull()?.bucketName ?: "Album",
                cover = list.firstOrNull(),
                count = list.size,
                items = list
            )
        }.sortedByDescending { it.items.firstOrNull()?.dateMillis ?: 0L }
    }
}
