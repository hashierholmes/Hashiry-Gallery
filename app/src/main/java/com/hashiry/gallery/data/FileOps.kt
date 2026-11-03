package com.hashiry.gallery.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.hashiry.gallery.model.MediaItem
import com.hashiry.gallery.model.TrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * File operations that go through Android's own media APIs so that the system
 * keeps ownership of the user's originals. Nothing is deleted or changed
 * without an explicit user action, and deletions the app is not allowed to make
 * are handed to the system's own confirmation dialog.
 */
object FileOps {


    /** Direct filesystem deletion for PSD files when the user explicitly granted All files access. */
    suspend fun deletePsdWithAllFilesAccess(context: Context, item: MediaItem): Boolean =
        withContext(Dispatchers.IO) {
            if (!item.isPsd || Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                !runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)) return@withContext false
            runCatching {
                val path = context.contentResolver.query(
                    item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
                val file = path?.let(::File) ?: return@runCatching false
                if (!file.isFile || !file.name.endsWith(".psd", true)) return@runCatching false
                val deleted = file.delete()
                if (deleted) {
                    runCatching { android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null) }
                }
                deleted
            }.getOrDefault(false)
        }

    // ------------------------------------------------------------------ share

    fun shareIntent(items: List<MediaItem>): Intent {
        val intent = if (items.size == 1) {
            val item = items.first()
            Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
                putExtra(Intent.EXTRA_STREAM, item.uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, "Share")
    }

    // ------------------------------------------------------------ copy / move

    suspend fun copyInto(context: Context, item: MediaItem, dir: DocumentFile): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = item.mimeType.ifBlank { "application/octet-stream" }
                val target = dir.createFile(mime, availableName(dir, item.name)) ?: return@runCatching null
                val input = context.contentResolver.openInputStream(item.uri)
                    ?: return@runCatching null
                input.use { source ->
                    context.contentResolver.openOutputStream(target.uri)?.use { sink ->
                        source.copyTo(sink, DEFAULT_BUFFER_SIZE * 4)
                    }
                }
                target.uri
            }.getOrNull()
        }

    private fun availableName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (index < 500) {
            val candidate = "$base ($index)$ext"
            if (dir.findFile(candidate) == null) return candidate
            index++
        }
        return "$base-${System.currentTimeMillis()}$ext"
    }

    // ----------------------------------------------------------------- rename

    suspend fun rename(context: Context, item: MediaItem, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                context.contentResolver.update(item.uri, values, null, null) > 0
            }.getOrDefault(false)
        }

    /** Android 11+ asks the user to allow modifying media the app does not own. */
    fun writeConsent(context: Context, uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        }.getOrNull()
    }

    // ----------------------------------------------------------------- delete

    /** The system dialog that confirms deleting other apps' media (Android 11+). */
    fun deleteConsent(context: Context, uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } catch (error: Exception) {
            android.util.Log.e("HashiryFileOps", "MediaStore delete request failed for ${uris.size} item(s)", error)
            null
        }
    }

    /** Delete each URI independently so partial success is never mistaken for total success. */
    suspend fun deleteNow(context: Context, uris: List<Uri>): Set<Uri> = withContext(Dispatchers.IO) {
        uris.filterTo(linkedSetOf()) { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) > 0 }
                .getOrDefault(false)
        }
    }

    // ------------------------------------------------------ trash (soft delete)

    /**
     * Copies the item's bytes into the app's private trash folder and returns
     * the index entry. The original is untouched at this point.
     */
    suspend fun copyIntoTrash(context: Context, item: MediaItem, trashDir: File): TrashEntry? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!trashDir.exists()) trashDir.mkdirs()
                val extension = item.name.substringAfterLast('.', "")
                val fileName = "${UUID.randomUUID()}${if (extension.isNotBlank()) ".$extension" else ""}"
                val destination = File(trashDir, fileName)
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 4)
                    }
                } ?: return@runCatching null
                TrashEntry(
                    id = UUID.randomUUID().toString(),
                    name = item.name,
                    mimeType = item.mimeType,
                    isVideo = item.isVideo,
                    sizeBytes = item.sizeBytes,
                    width = item.width,
                    height = item.height,
                    dateMillis = item.dateMillis,
                    deletedAt = System.currentTimeMillis(),
                    fileName = fileName,
                    originalRelativePath = readOriginalRelativePath(context, item.uri)
                )
            }.getOrNull()
        }

    /** Writes a trashed item back into the device's media collection. */
    suspend fun restore(context: Context, entry: TrashEntry, trashDir: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = File(trashDir, entry.fileName)
                if (!source.exists()) return@runCatching false
                val collection = when {
                    entry.isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    entry.name.endsWith(".psd", ignoreCase = true) -> MediaStore.Files.getContentUri("external")
                    else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val defaultFolder =
                    if (entry.isVideo) "${Environment.DIRECTORY_MOVIES}/Hashiry Gallery/"
                    else "${Environment.DIRECTORY_PICTURES}/Hashiry Gallery/"
                // MediaStore creates the RELATIVE_PATH directory when inserting, including
                // when the original folder was deleted after this item was trashed.
                val restorePath = entry.originalRelativePath
                    .takeIf { it.isNotBlank() }
                    ?.let { if (it.endsWith("/")) it else "$it/" }
                    ?: defaultFolder
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, entry.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    } else {
                        val externalRoot = Environment.getExternalStorageDirectory()
                        val targetFolder = File(externalRoot, restorePath)
                        if (!targetFolder.exists()) targetFolder.mkdirs()
                        put(MediaStore.MediaColumns.DATA, File(targetFolder, entry.name).absolutePath)
                    }
                }
                val uri = context.contentResolver.insert(collection, values)
                    ?: return@runCatching false
                val copied = try {
                    val output = context.contentResolver.openOutputStream(uri)
                    if (output == null) false else {
                        output.use { sink -> source.inputStream().use { it.copyTo(sink, DEFAULT_BUFFER_SIZE * 4) } }
                        true
                    }
                } catch (_: Exception) {
                    false
                }
                if (!copied) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    return@runCatching false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    val updated = runCatching { context.contentResolver.update(uri, publish, null, null) > 0 }.getOrDefault(false)
                    if (!updated) {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                        return@runCatching false
                    }
                }
                // Keep the private backup until the MediaStore copy is fully published.
                source.delete()
                true
            }.getOrDefault(false)
        }

    /** Read the source item's actual folder before its original is removed. */
    private fun readOriginalRelativePath(context: Context, uri: Uri): String {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                    null, null, null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index).orEmpty() else ""
                }.orEmpty()
            } else {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null, null, null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (cursor.moveToFirst() && index >= 0) {
                        val absolute = cursor.getString(index).orEmpty()
                        val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
                        if (absolute.startsWith(root)) absolute.removePrefix(root).substringBeforeLast('/', "")
                            .let { if (it.isBlank()) "" else "$it/" }
                        else ""
                    } else ""
                }.orEmpty()
            }
        }.getOrDefault("")
    }

    fun deleteTrashFile(trashDir: File, entry: TrashEntry) {
        runCatching { File(trashDir, entry.fileName).delete() }
    }
}
