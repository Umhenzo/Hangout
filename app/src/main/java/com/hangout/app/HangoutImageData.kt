package com.hangout.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Keeps small, compressed JPEGs inside Firestore so Hangout can send photos
 * without needing a Firebase Storage bucket or billing account.
 *
 * Firestore documents are limited to 1 MiB, so uploaded images are capped at
 * 450 KB before Base64 encoding. This is intended for a small private group,
 * not for a large public messaging service.
 */
object HangoutImageData {
    private const val PREFIX = "data:image/jpeg;base64,"
    private const val MAX_JPEG_BYTES = 450_000
    private const val MAX_INITIAL_DIMENSION = 1200

    fun encode(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_INITIAL_DIMENSION) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        try {
            var quality = 82
            while (true) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val bytes = stream.toByteArray()
                if (bytes.size <= MAX_JPEG_BYTES) {
                    return PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }

                if (quality > 50) {
                    quality -= 8
                } else {
                    val newWidth = (bitmap.width * 0.8f).toInt().coerceAtLeast(1)
                    val newHeight = (bitmap.height * 0.8f).toInt().coerceAtLeast(1)
                    if (maxOf(bitmap.width, bitmap.height) <= 320) return null
                    val smaller = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    if (smaller !== bitmap) bitmap.recycle()
                    bitmap = smaller
                    quality = 78
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Returns a Bitmap for our Firestore data URI, or the URL for older messages. */
    fun model(value: String?): Any? {
        if (value.isNullOrBlank()) return null
        if (!value.startsWith(PREFIX)) return value
        return try {
            val bytes = Base64.decode(value.removePrefix(PREFIX), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun isDataUri(value: String?): Boolean = value?.startsWith(PREFIX) == true
}
