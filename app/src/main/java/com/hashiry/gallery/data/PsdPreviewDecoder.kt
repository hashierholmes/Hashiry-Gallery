package com.hashiry.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.zip.InflaterInputStream

/**
 * Small, dependency-free reader for the flattened composite preview in PSD v1 files.
 * Supports 8-bit RGB and grayscale documents using raw, PackBits/RLE, or ZIP data.
 * Layer editing is not attempted; the original PSD is never modified.
 */
object PsdPreviewDecoder {
    private const val MAX_PIXELS = 16_000_000L
    private const val MAX_DIMENSION = 30_000

    fun decode(context: Context, uri: Uri, maxDimension: Int): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { decode(it, maxDimension) }
    } catch (_: Exception) { null }

    private fun decode(input: InputStream, maxDimension: Int): Bitmap? {
        val data = DataInputStream(BufferedInputStream(input, 64 * 1024))
        val signature = ByteArray(4); data.readFully(signature)
        if (!signature.contentEquals(byteArrayOf(0x38, 0x42, 0x50, 0x53))) return null // 8BPS
        if (data.readUnsignedShort() != 1) return null
        data.skipFully(6)
        val channels = data.readUnsignedShort()
        val height = data.readInt().toLong() and 0xffffffffL
        val width = data.readInt().toLong() and 0xffffffffL
        val depth = data.readUnsignedShort()
        val mode = data.readUnsignedShort()
        if (width !in 1..MAX_DIMENSION.toLong() || height !in 1..MAX_DIMENSION.toLong()) return null
        if (width * height > MAX_PIXELS || depth != 8 || channels !in 1..5) return null
        if (mode != 3 && mode != 1) return null // RGB or grayscale only
        if (mode == 3 && channels < 3) return null
        if (mode == 1 && channels < 1) return null
        data.skipFully(data.readInt().toLong() and 0xffffffffL) // color-mode data
        data.skipFully(data.readInt().toLong() and 0xffffffffL) // image resources
        data.skipFully(data.readInt().toLong() and 0xffffffffL) // layer/mask section
        val compression = data.readUnsignedShort()
        val w = width.toInt(); val h = height.toInt(); val planeSize = w * h
        val requiredChannels = if (mode == 3) 3 else 1
        val decodeChannels = minOf(channels, if (channels > requiredChannels) requiredChannels + 1 else requiredChannels)
        val planes = Array(decodeChannels) { ByteArray(planeSize) }
        when (compression) {
            0 -> planes.forEach { data.readFully(it) }
            1 -> {
                val rowCount = channels.toLong() * h
                if (rowCount > 150_000L) return null
                val rowLengths = IntArray(rowCount.toInt()) { data.readUnsignedShort() }
                var rowIndex = 0
                for (channel in 0 until channels) {
                    for (y in 0 until h) {
                        val packedLength = rowLengths[rowIndex++]
                        val packed = ByteArray(packedLength); data.readFully(packed)
                        if (channel < decodeChannels && !unpackBits(packed, planes[channel], y * w, w)) return null
                    }
                }
            }
            2, 3 -> {
                val expected = planeSize.toLong() * channels
                if (expected > 100_000_000L) return null
                val inflated = ByteArray(expected.toInt())
                val inflater = InflaterInputStream(data)
                var offset = 0
                while (offset < inflated.size) {
                    val n = inflater.read(inflated, offset, inflated.size - offset)
                    if (n < 0) return null
                    offset += n
                }
                if (compression == 3) {
                    // PSD ZIP prediction: each row is delta-coded independently.
                    for (c in 0 until channels) for (y in 0 until h) {
                        val start = (c * planeSize) + y * w
                        for (x in 1 until w) inflated[start + x] = (inflated[start + x].toInt() + inflated[start + x - 1].toInt()).toByte()
                    }
                }
                for (c in 0 until decodeChannels) System.arraycopy(inflated, c * planeSize, planes[c], 0, planeSize)
            }
            else -> return null
        }
        val sample = maxOf(1, kotlin.math.ceil(maxOf(w, h).toDouble() / maxDimension.coerceAtLeast(1)).toInt())
        val outW = (w + sample - 1) / sample; val outH = (h + sample - 1) / sample
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(outW * outH)
        var dst = 0
        for (y in 0 until h step sample) {
            for (x in 0 until w step sample) {
                val i = y * w + x
                val r: Int; val g: Int; val b: Int
                if (mode == 1) { val gray = planes[0][i].toInt() and 255; r = gray; g = gray; b = gray }
                else { r = planes[0][i].toInt() and 255; g = planes[1][i].toInt() and 255; b = planes[2][i].toInt() and 255 }
                val alpha = if (channels > requiredChannels && decodeChannels > requiredChannels) planes[requiredChannels][i].toInt() and 255 else 255
                pixels[dst++] = Color.argb(alpha, r, g, b)
            }
        }
        bitmap.setPixels(pixels, 0, outW, 0, 0, outW, outH)
        return bitmap
    }

    private fun unpackBits(source: ByteArray, target: ByteArray, offset: Int, rowWidth: Int): Boolean {
        var src = 0; var dst = offset; val end = offset + rowWidth
        while (src < source.size && dst < end) {
            val n = source[src++].toInt()
            when {
                n in 0..127 -> {
                    val count = n + 1
                    if (src + count > source.size || dst + count > end) return false
                    System.arraycopy(source, src, target, dst, count); src += count; dst += count
                }
                n in -127..-1 -> {
                    if (src >= source.size) return false
                    val value = source[src++]
                    val count = 1 - n
                    if (dst + count > end) return false
                    java.util.Arrays.fill(target, dst, dst + count, value); dst += count
                }
                else -> Unit // -128 is a no-op
            }
        }
        return dst == end
    }

    private fun DataInputStream.skipFully(count: Long) {
        if (count < 0 || count > 512L * 1024L * 1024L) throw EOFException("Invalid PSD section size")
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) { if (read() < 0) throw EOFException(); remaining-- } else remaining -= skipped
        }
    }
}
