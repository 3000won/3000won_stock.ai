package com.won3000.glowplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.won3000.glowplayer.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads album art (embedded picture or album thumbnail) with a small in-memory cache. */
object ArtworkLoader {

    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(
        context: Context,
        key: String,
        songUri: Uri?,
        albumArtUri: Uri?,
        data: ByteArray?,
        sizePx: Int,
    ): ImageBitmap? {
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            songUri?.let { thumbnail(context, it, sizePx) }
                ?: albumArtUri?.let { decodeUri(context, it, sizePx) }
                ?: data?.let { decodeBytes(it, sizePx) }
        } ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private fun thumbnail(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeUri(context: Context, uri: Uri, sizePx: Int): Bitmap? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeBytes(data: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx)
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}

/**
 * Artwork for [mediaId]. Keeps showing the previous picture while the next one loads,
 * so switching songs cross-fades instead of flashing the placeholder.
 */
@Composable
fun rememberArtwork(
    mediaId: String?,
    artworkUri: Uri?,
    artworkData: ByteArray?,
    sizePx: Int,
): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val key = mediaId?.let { "$it@$sizePx" }
    var bitmap by remember { mutableStateOf(key?.let(ArtworkLoader::peek)) }
    LaunchedEffect(key, artworkData != null) {
        bitmap = if (key == null) {
            null
        } else {
            ArtworkLoader.peek(key) ?: ArtworkLoader.load(
                context = context,
                key = key,
                songUri = mediaId?.toLongOrNull()?.let { MusicRepository.contentUriFor(it) },
                albumArtUri = artworkUri,
                data = artworkData,
                sizePx = sizePx,
            )
        }
    }
    return bitmap
}
