package com.danswirsky.myeats.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream

/**
 * Compresses a picked gallery image to a small JPEG before upload.
 * A camera photo of 5-8 MB becomes ~150-300 KB — faster feed loading
 * and a fraction of the Storage quota.
 */
object ImageUtils {

    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 75

    /** Runs on a background thread; the callback is delivered on the main thread. */
    fun compressToJpeg(context: Context, uri: Uri, callback: (ByteArray?) -> Unit) {
        Thread {
            val bytes = try {
                // Pass 1: read only the dimensions
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                // Choose a power-of-two sample size that gets us under MAX_DIMENSION
                var sampleSize = 1
                while (boundsOptions.outWidth / sampleSize > MAX_DIMENSION ||
                    boundsOptions.outHeight / sampleSize > MAX_DIMENSION
                ) {
                    sampleSize *= 2
                }

                // Pass 2: decode downsampled, then compress to JPEG
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }

                val output = ByteArrayOutputStream()
                bitmap?.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                bitmap?.recycle()
                output.toByteArray().takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
            Handler(Looper.getMainLooper()).post { callback(bytes) }
        }.start()
    }
}
