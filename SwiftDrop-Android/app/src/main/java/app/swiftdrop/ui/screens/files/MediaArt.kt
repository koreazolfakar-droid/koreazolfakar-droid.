package app.swiftdrop.ui.screens.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.swiftdrop.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// SwiftDrop @performance Codex 12/10/2026 Decode only visible media and cache bounded thumbnails.
private object ArtCache {
    val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun clear() = thumbnails.evictAll()
}

fun clearMediaThumbnails() = ArtCache.clear()

private fun decodeScaled(context: Context, uri: Uri, maxPixels: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxPixels || bounds.outHeight / sample > maxPixels) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun loadArt(context: Context, uri: Uri, kind: MediaKind, fullSize: Boolean): Bitmap? = runCatching {
    if (fullSize) return@runCatching decodeScaled(context, uri, 2048)
    if (Build.VERSION.SDK_INT >= 29) return@runCatching context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
    if (kind == MediaKind.Videos) {
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(context, uri); retriever.frameAtTime } finally { retriever.release() }
    } else decodeScaled(context, uri, 320)
}.getOrNull()

@Composable
fun MediaArt(uri: Uri, kind: MediaKind, modifier: Modifier = Modifier, fullSize: Boolean = false) {
    val context = LocalContext.current
    val key = "${if (fullSize) "large" else "thumb"}:$uri"
    val bitmap by produceState<Bitmap?>(ArtCache.thumbnails.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            loadArt(context, uri, kind, fullSize)?.also { if (!fullSize) ArtCache.thumbnails.put(key, it) }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
}
