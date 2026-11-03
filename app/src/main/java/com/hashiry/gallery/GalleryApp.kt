package com.hashiry.gallery

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Hashiry Gallery is a single-process, offline application.
 * Coil handles non-standard format decodes, video frames, and fallback requests.
 */
class GalleryApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val cacheDir = File(this.cacheDir, "thumbnails")
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                // Android's ImageDecoder supports animated WebP from API 28 onward.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .allowHardware(true)
            .build()
    }
}
