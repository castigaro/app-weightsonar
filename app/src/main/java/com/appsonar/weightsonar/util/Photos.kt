package com.appsonar.weightsonar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.appsonar.weightsonar.BuildConfig
import java.io.File
import java.io.FileOutputStream

object Photos {

    private const val MAX_DIMENSION = 1600 // Etiketten brauchen mehr Auflösung als Objekte
    private const val JPEG_QUALITY = 85

    fun newPhotoFile(context: Context): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "photo_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

    /**
     * Verkleinert das Foto auf max. [MAX_DIMENSION] Pixel Kantenlänge und
     * überschreibt die Datei. Hält die API-Requests klein, lässt aber genug
     * Auflösung für das Kleingedruckte auf Etiketten.
     */
    fun downscale(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return

        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }

        FileOutputStream(file).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (finalBitmap !== bitmap) finalBitmap.recycle()
        bitmap.recycle()
    }

    fun toBase64(file: File): String =
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
}
