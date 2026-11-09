package com.eventms.mobile.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.core.outcomeOf
import com.eventms.mobile.data.remote.MediaApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover images picked from the device.
 *
 * The picked file is re-encoded before it leaves the phone. A modern camera
 * produces 8–12 MB frames, which would blow the upload limit and waste the
 * attendee's data every time a card scrolls past — a 1600px JPEG is
 * indistinguishable on a phone screen and lands around 200–400 KB.
 */
@Singleton
class MediaRepository @Inject constructor(
    private val api: MediaApi,
    @ApplicationContext private val context: Context
) {

    suspend fun uploadCoverImage(uri: Uri): Outcome<String> = outcomeOf {
        val bytes = withContext(Dispatchers.IO) { prepare(uri) }
        val response = api.upload(
            contentType = "image/jpeg",
            image = bytes.toRequestBody("image/jpeg".toMediaType())
        )
        response.url
            ?: throw IllegalStateException(response.error ?: "The image could not be uploaded")
    }

    /** Decodes at a sane size, corrects orientation, and re-encodes as JPEG. */
    private fun prepare(uri: Uri): ByteArray {
        val resolver = context.contentResolver

        // First pass reads only the dimensions, so a huge image never has to be
        // fully decoded into memory just to find out how big it is.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalStateException("That image could not be read")

        val rotated = applyOrientation(uri, decoded)
        val scaled = scaleDown(rotated)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        return out.toByteArray()
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        // Halve until the decode is within a couple of times the target, then
        // do the final resize precisely.
        while (longest / 2 >= MAX_DIMENSION) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_DIMENSION) return source

        val ratio = MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Phone cameras record rotation in EXIF rather than rotating the pixels, so
     * a portrait shot would otherwise upload sideways.
     */
    private fun applyOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap

        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85
    }
}
