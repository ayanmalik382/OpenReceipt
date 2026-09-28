package com.receiptbook.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Shared picture handling for business logos and customer/supplier photos. Everything is stored
 * as a compressed Base64 JPEG string directly on the entity - simplest possible fit for the
 * existing offline-first sync design (which already ships whole rows as JSON).
 *
 * Any picked photo, however large, is automatically downsampled and JPEG-compressed to fit under
 * [TARGET_BYTES] before it's ever stored.
 */
object ImageStore {
    private const val MAX_DIMENSION = 400       // longer side, in pixels
    private const val TARGET_BYTES = 100 * 1024  // ~100KB raw JPEG (~135KB once Base64-encoded)
    private const val MIN_QUALITY = 35

    /** Returns null only if the image genuinely can't be read. */
    suspend fun compressToBase64(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeAndOrient(ctx, uri) ?: return@withContext null
            try {
                var quality = 90
                var bytes: ByteArray
                do {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    bytes = out.toByteArray()
                    quality -= 12
                } while (bytes.size > TARGET_BYTES && quality >= MIN_QUALITY)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Decodes a Base64 string saved by [compressToBase64] back into a Bitmap for display. */
    fun decode(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeAndOrient(ctx: Context, uri: Uri): Bitmap? {
        val resolver = ctx.contentResolver

        // Pass 1: read only the dimensions. NOTE: in this mode decodeStream() ALWAYS returns null
        // by design - the size comes back through `bounds` - so that null must not be treated as a
        // failure (treating it as one made every image "unreadable").
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION * 2 || bounds.outHeight / sample > MAX_DIMENSION * 2) sample *= 2

        // Pass 2: decode at that sample size (avoids running out of memory on big camera photos).
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        // Camera photos are often stored sideways with an EXIF rotation flag - apply it now.
        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) { 0 }

        val scale = MAX_DIMENSION.toFloat() / maxOf(raw.width, raw.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        var result = raw
        if (!matrix.isIdentity) {
            result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (result !== raw) raw.recycle()
        }

        // JPEG has no transparency: a transparent PNG logo would turn black. Flatten onto white first.
        if (result.hasAlpha()) {
            val flat = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(result, 0f, 0f, null) }
            result.recycle()
            result = flat
        }
        return result
    }
}