package app.swiftdrop.ui.screens.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.swiftdrop.R
import app.swiftdrop.ui.components.AppIcon
import app.swiftdrop.ui.components.FileCategory
import app.swiftdrop.ui.components.FileTypeBadge
import app.swiftdrop.ui.components.fileCategory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.swiftdrop.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// SwiftDrop @performance Codex 12/10/2026 Decode only visible media and cache bounded thumbnails.
private object ArtCache {
    private val maxCacheBytes = (Runtime.getRuntime().maxMemory() / 16L)
        .coerceIn(4L * 1024 * 1024, 16L * 1024 * 1024).toInt()
    val thumbnails = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun clear() = thumbnails.evictAll()
}

fun clearMediaThumbnails() = ArtCache.clear()

private fun decodeScaled(context: Context, uri: Uri, maxPixels: Int): Bitmap? {
    val resolver = context.contentResolver
    // Decode the bounds first to prevent loading a multi-megapixel photo into UI memory.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        var sample = 1
        while (bounds.outWidth / sample > maxPixels || bounds.outHeight / sample > maxPixels)
            sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
        if (bitmap != null) return bitmap
    }
    // Some HEIC/AVIF and provider-backed photos fail BitmapFactory or loadThumbnail.
    // ImageDecoder offers a second bounded path on Android 9+; no full-res fallback.
    if (Build.VERSION.SDK_INT >= 28) return runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val largest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val divisor = ((largest + maxPixels - 1L) / maxPixels).toInt().coerceAtLeast(1)
            decoder.setTargetSampleSize(divisor)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
    return null
}

private fun videoFrame(context: Context, uri: Uri): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        // Scaled frame extraction prevents giant uncompressed video frames in the grid.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,240,240)
        } else {
            // Android 8.0 (API 26) does not expose getScaledFrameAtTime.
            // Decode only when needed and recycle the full frame after scaling.
            retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { source ->
                val largest = maxOf(source.width,source.height).coerceAtLeast(1)
                if (largest <= 320) source else {
                    val ratio = 320f / largest
                    val scaled = Bitmap.createScaledBitmap(source,
                        (source.width * ratio).toInt().coerceAtLeast(1),
                        (source.height * ratio).toInt().coerceAtLeast(1),true)
                    if (scaled !== source) source.recycle()
                    scaled
                }
            }
        }
    } finally { retriever.release() }
}.getOrNull()

private fun loadArt(context: Context, uri: Uri, kind: MediaKind, fullSize: Boolean): Bitmap? {
    if (fullSize) return if(kind == MediaKind.Videos) videoFrame(context,uri)
        else decodeScaled(context,uri,2048)
    // Many OEM/SAF providers throw or return null for loadThumbnail; decode the URI.
    val thumbnail = if (Build.VERSION.SDK_INT >= 29) runCatching {
        context.contentResolver.loadThumbnail(uri, Size(240,240), null)
    }.getOrNull() else null
    return thumbnail ?: if (kind == MediaKind.Videos) videoFrame(context,uri)
        else decodeScaled(context,uri,320)
}

/** No full-sized images in the thumbnail cache; only the visible grid items request decoding. */
fun thumbnailCacheBytes(): Int = ArtCache.thumbnails.size()

@Composable
fun MediaArt(uri: Uri, kind: MediaKind, modifier: Modifier = Modifier, fullSize: Boolean = false) {
    val context = LocalContext.current
    val key = "${if (fullSize) "large" else "thumb"}:$uri"
    val bitmap by produceState<Bitmap?>(ArtCache.thumbnails.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            loadArt(context, uri, kind, fullSize)?.also { if (!fullSize) ArtCache.thumbnails.put(key, it) }
        }
    }
    Box(modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment=Alignment.Center) {
        if(bitmap==null) {
            AppIcon(if(kind==MediaKind.Videos) R.drawable.ic_videos else R.drawable.ic_photos,
                modifier=Modifier, tint=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),
                contentScale=if(fullSize) ContentScale.Fit else ContentScale.Crop)
        }
    }
}

/** Actual image thumbnails in the sender selection/transfer queue, not generic image glyphs. */
@Composable
fun FilePreviewBadge(uri: Uri, name: String, modifier: Modifier = Modifier.size(48.dp)) {
    val context = LocalContext.current
    val mime = remember(uri) { runCatching { context.contentResolver.getType(uri) }.getOrNull() }
    if (fileCategory(name,mime) == FileCategory.Photo) {
        Surface(modifier,shape=MaterialTheme.shapes.small) {
            MediaArt(uri,MediaKind.Photos,Modifier.fillMaxSize())
        }
    } else FileTypeBadge(name,modifier,mimeType=mime)
}
