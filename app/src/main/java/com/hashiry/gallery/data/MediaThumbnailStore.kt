package com.hashiry.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.CancellationSignal
import android.os.Process
import android.util.LruCache
import android.util.Size
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.hashiry.gallery.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Two-level thumbnail cache optimized for large date groups and fast scrolling.
 *
 * All decoding runs at THREAD_PRIORITY_BACKGROUND to prevent frame drops on the UI thread.
 * Reusable bitmap buffers (inBitmap) eliminate garbage collection pauses during long scrolls.
 */
object MediaThumbnailStore {
    private const val MEMORY_MIN_KB = 32 * 1024
    private const val MEMORY_MAX_KB = 96 * 1024
    private const val DISK_LIMIT_BYTES = 192L * 1024L * 1024L
    private const val THUMBNAIL_SIZE = 256
    private const val JPEG_QUALITY = 78
    private const val PNG_CACHE_SUFFIX = ".png"
    private const val CACHE_VERSION = "v2"

    // Pool of reusable bitmap buffers to prevent memory allocation during fast scroll.
    private val bitmapPool = ArrayDeque<Bitmap>(16)

    private fun acquireBitmapFromPool(): Bitmap? = synchronized(bitmapPool) {
        bitmapPool.pollLast()
    }

    private fun releaseBitmapToPool(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable ||
            bitmap.width != THUMBNAIL_SIZE || bitmap.height != THUMBNAIL_SIZE ||
            bitmap.config != Bitmap.Config.RGB_565
        ) {
            return
        }
        synchronized(bitmapPool) {
            if (bitmapPool.size < 16) {
                bitmapPool.addLast(bitmap)
            }
        }
    }

    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val runtimeBudgetKb = (Runtime.getRuntime().maxMemory() / 1024L / 4L)
            .coerceIn(MEMORY_MIN_KB.toLong(), MEMORY_MAX_KB.toLong())
            .toInt()
        object : LruCache<String, Bitmap>(runtimeBudgetKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)

            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
                if (evicted && oldValue !== newValue) {
                    releaseBitmapToPool(oldValue)
                }
            }
        }
    }

    // Decoder thread pool running strictly at background priority so the UI thread is never starved.
    private val decodeExecutor = ThreadPoolExecutor(
        3, 3, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, "Hashiry-ThumbnailDecoder").apply {
                isDaemon = true
            }
        }
    )
    private val decodeDispatcher = decodeExecutor.asCoroutineDispatcher()

    // Bounded concurrency for MediaStore generation.
    private val generationLimiter = Semaphore(3)

    // Dedicated low-priority disk writer.
    private val writesSincePrune = AtomicInteger(0)
    private val diskWriteScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val diskWriter = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1024),
        { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                runnable.run()
            }, "Hashiry-ThumbnailDiskWriter").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    @Volatile private var cacheDirInitialized = false
    private lateinit var thumbnailDir: File

    private fun getThumbnailDir(context: Context): File {
        if (!cacheDirInitialized) {
            synchronized(this) {
                if (!cacheDirInitialized) {
                    thumbnailDir = File(context.applicationContext.noBackupFilesDir, "hashiry_media_thumbnails").apply {
                        if (!exists()) mkdirs()
                    }
                    cacheDirInitialized = true
                }
            }
        }
        return thumbnailDir
    }

    fun getThumbnailKey(item: MediaItem): String =
        "${item.id}_${item.dateMillis}_${item.sizeBytes}"

    fun getMemory(key: String): Bitmap? = memoryCache.get(key)

    suspend fun load(
        context: Context,
        item: MediaItem,
        key: String
    ): Bitmap? = withContext(decodeDispatcher) {
        // 1. L1 Memory cache
        memoryCache.get(key)?.let { return@withContext it }

        // 2. L2 Disk cache (Fast, zero-allocation via inBitmap)
        val alphaCapable = item.mimeType.equals("image/png", true) ||
            item.mimeType.equals("image/webp", true) ||
            item.mimeType.equals("image/gif", true) ||
            item.mimeType.equals("image/avif", true)
        val cacheFile = File(
            getThumbnailDir(context),
            "t_${CACHE_VERSION}_${item.id}_${item.dateMillis}_${item.sizeBytes}" + if (alphaCapable) PNG_CACHE_SUFFIX else ".jpg"
        )
        if (cacheFile.isFile && cacheFile.length() > 0L) {
            val cached = decodeCachedBitmap(cacheFile)
            if (cached != null) {
                memoryCache.put(key, cached)
                return@withContext cached
            }
            cacheFile.delete()
        }

        // 3. L3 Generation (Gated to keep MediaStore responsive)
        generationLimiter.withPermit {
            memoryCache.get(key)?.let { return@withPermit it }
            if (cacheFile.isFile && cacheFile.length() > 0L) {
                val cached = decodeCachedBitmap(cacheFile)
                if (cached != null) {
                    memoryCache.put(key, cached)
                    return@withPermit cached
                }
            }

            if (item.isPsd) {
                val psdBitmap = PsdPreviewDecoder.decode(context, item.uri, THUMBNAIL_SIZE)
                if (psdBitmap != null) {
                    memoryCache.put(key, psdBitmap)
                    enqueueDiskWrite(cacheFile, psdBitmap)
                    return@withPermit psdBitmap
                }
                return@withPermit null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signal = CancellationSignal()
                val job = currentCoroutineContext().job
                job.invokeOnCompletion { signal.cancel() }
                try {
                    val generated = context.contentResolver.loadThumbnail(
                        item.uri,
                        Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE),
                        signal
                    )
                    if (generated != null) {
                        memoryCache.put(key, generated)
                        enqueueDiskWrite(cacheFile, generated)
                        return@withPermit generated
                    }
                } catch (e: Exception) {
                    if (e is android.os.OperationCanceledException || e is CancellationException) {
                        throw CancellationException("Thumbnail load canceled", e)
                    }
                }
            }

            val fallback = decodeFallback(context, item)
            if (fallback != null) {
                memoryCache.put(key, fallback)
                enqueueDiskWrite(cacheFile, fallback)
                return@withPermit fallback
            }

            null
        }
    }

    private fun decodeCachedBitmap(file: File): Bitmap? {
        // PNG cache entries may contain transparent pixels. RGB_565 has no alpha
        // channel, so decoding PNG thumbnails into it permanently turns transparency
        // into opaque pixels (often black). Only opaque JPEG entries may use the pool.
        val preservesAlpha = file.extension.equals("png", true)
        if (preservesAlpha) {
            return try {
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = false
                        inSampleSize = 1
                    }
                )
            } catch (_: Throwable) {
                null
            }
        }

        val reusable = acquireBitmapFromPool()
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = true
            inSampleSize = 1
            if (reusable != null) inBitmap = reusable
        }
        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: IllegalArgumentException) {
            reusable?.let { if (!it.isRecycled) it.recycle() }
            try {
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inMutable = true
                    }
                )
            } catch (_: Throwable) {
                null
            }
        } catch (_: Throwable) {
            reusable?.let { if (!it.isRecycled) it.recycle() }
            null
        }
    }

    private fun enqueueDiskWrite(file: File, bitmap: Bitmap) {
        diskWriter.execute {
            writeCachedBitmap(file, bitmap)
        }
    }

    private fun writeCachedBitmap(file: File, bitmap: Bitmap) {
        if (file.exists() && file.length() > 0L) return
        val parent = file.parentFile ?: return
        val temp = File(parent, "${file.name}.${System.currentTimeMillis()}.tmp")
        var copyToRecycle: Bitmap? = null
        try {
            val compressableBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) {
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                copyToRecycle = copy
                copy ?: return
            } else {
                bitmap
            }
            FileOutputStream(temp).use { output ->
                val format = if (file.extension.equals("png", true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (format == Bitmap.CompressFormat.PNG) 100 else JPEG_QUALITY
                if (!compressableBitmap.compress(format, quality, output)) return
                output.flush()
            }
            if (temp.length() > 0L) {
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
                pruneDiskCacheIfNeeded(parent)
            }
        } catch (_: Exception) {
            // Ignore disposable cache failures
        } finally {
            copyToRecycle?.recycle()
            if (temp.exists()) temp.delete()
        }
    }

    private fun pruneDiskCacheIfNeeded(directory: File) {
        if (writesSincePrune.incrementAndGet() < 500) return
        writesSincePrune.set(0)
        diskWriteScope.launch {
            runCatching {
                val files = directory.listFiles { f -> f.isFile && (f.name.endsWith(".jpg") || f.name.endsWith(".png")) } ?: return@launch
                var total = files.sumOf { it.length() }
                if (total <= DISK_LIMIT_BYTES) return@launch
                val target = (DISK_LIMIT_BYTES * 0.80).toLong()
                val sorted = files.sortedBy { it.lastModified() }
                for (f in sorted) {
                    if (total <= target) break
                    val len = f.length()
                    if (f.delete()) total -= len
                }
            }
        }
    }

    private suspend fun decodeFallback(context: Context, item: MediaItem): Bitmap? {
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(item.uri)
                .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .allowRgb565(true)
                .build()
            val result = context.imageLoader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }
}