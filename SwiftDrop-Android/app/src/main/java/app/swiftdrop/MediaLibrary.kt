package app.swiftdrop

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

// SwiftDrop @feature Codex 12/10/2026 Keep media access independent of Nearby permissions.
enum class MediaKind(val permission: String) {
    Photos("android.permission.READ_MEDIA_IMAGES"),
    Videos("android.permission.READ_MEDIA_VIDEO"),
    Music("android.permission.READ_MEDIA_AUDIO")
}

data class MediaAccess(val allowed: Boolean, val partial: Boolean = false)

fun requestedMediaPermissions(kind: MediaKind, sdk: Int): Array<String> = when {
    sdk <= 32 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    sdk >= 34 && kind != MediaKind.Music -> arrayOf(kind.permission, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
    else -> arrayOf(kind.permission)
}

fun mediaAccess(kind: MediaKind, sdk: Int, granted: (String) -> Boolean): MediaAccess = when {
    sdk <= 32 -> MediaAccess(granted(Manifest.permission.READ_EXTERNAL_STORAGE))
    granted(kind.permission) -> MediaAccess(true)
    sdk >= 34 && kind != MediaKind.Music && granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") -> MediaAccess(true, true)
    else -> MediaAccess(false)
}

data class LocalMedia(
    val uri: Uri, val name: String, val size: Long, val duration: Long,
    val addedAtSeconds: Long = 0L,
    val artist: String? = null, val album: String? = null
)

/** Use the device's local calendar day to group media, never UTC date strings. */
fun mediaDayKey(epochSeconds: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    if (epochSeconds <= 0) "unknown" else
        java.time.Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate().toString()

/** Stable grouping is available only for chronological sorts, not filename/size sorts. */
fun groupedByDay(sort: MediaSort): Boolean = sort == MediaSort.Newest || sort == MediaSort.Oldest

/** Provider-side sort, not a sort of only the first few loaded pages. */
enum class MediaSort { Newest, Oldest, Name, Largest }

fun mediaSortOrder(sort: MediaSort): String {
    val id = MediaStore.MediaColumns._ID
    return when(sort) {
        MediaSort.Newest -> "${MediaStore.MediaColumns.DATE_ADDED} DESC, $id DESC"
        MediaSort.Oldest -> "${MediaStore.MediaColumns.DATE_ADDED} ASC, $id ASC"
        MediaSort.Name -> "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC, $id DESC"
        MediaSort.Largest -> "${MediaStore.MediaColumns.SIZE} DESC, $id DESC"
    }
}

// SwiftDrop @feature Codex 12/10/2026 Real provider-backed album, folder and artist navigation.
enum class MediaGrouping { Folder, Album, Artist }
data class MediaGroup(val id:String,val title:String,val count:Int,val cover:Uri)

private fun groupColumn(grouping:MediaGrouping):String = when(grouping) {
    MediaGrouping.Folder -> "bucket_id"
    MediaGrouping.Album -> MediaStore.Audio.Media.ALBUM_ID
    MediaGrouping.Artist -> MediaStore.Audio.Media.ARTIST_ID
}
private fun groupNameColumn(grouping:MediaGrouping):String = when(grouping) {
    MediaGrouping.Folder -> "bucket_display_name"
    MediaGrouping.Album -> MediaStore.Audio.Media.ALBUM
    MediaGrouping.Artist -> MediaStore.Audio.Media.ARTIST
}

/** Pages on the provider side; no media rows or file paths are loaded into memory up front. */
class MediaLibrary(private val resolver: ContentResolver) {
    fun changes() = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    suspend fun page(kind: MediaKind, search: String, offset: Int, limit: Int = 48,
        sort: MediaSort = MediaSort.Newest, grouping: MediaGrouping? = null, groupId:String? = null): List<LocalMedia> = withContext(Dispatchers.IO) {
        val collection = when (kind) {
            MediaKind.Photos -> MediaStore.Images.Media.getContentUri("external")
            MediaKind.Videos -> MediaStore.Video.Media.getContentUri("external")
            MediaKind.Music -> MediaStore.Audio.Media.getContentUri("external")
        }
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED) +
            if (kind == MediaKind.Photos) emptyArray() else arrayOf(
                if (kind == MediaKind.Videos) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION) +
            if(kind==MediaKind.Music) arrayOf(MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM) else emptyArray()
        val filtered=grouping!=null && groupId!=null
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                (if (kind == MediaKind.Music) "${MediaStore.Audio.Media.IS_MUSIC} = 1" else "1 = 1") +
                    (if (Build.VERSION.SDK_INT >= 29) " AND ${MediaStore.MediaColumns.IS_PENDING} = 0" else "") +
                    (if(filtered) " AND ${groupColumn(requireNotNull(grouping))} = ?" else "") +
                    (if (search.isBlank()) "" else " AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"))
            val selectionArgs=ArrayList<String>()
            if(filtered) selectionArgs.add(requireNotNull(groupId))
            if(search.isNotBlank()) selectionArgs.add("%${search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,selectionArgs.toTypedArray())
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, mediaSortOrder(sort))
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        val rows = ArrayList<LocalMedia>(limit)
        resolver.query(collection, columns, args, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val added = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val duration = if (kind == MediaKind.Photos) -1 else cursor.getColumnIndexOrThrow(
                if (kind == MediaKind.Videos) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val itemUri = ContentUris.withAppendedId(collection, cursor.getLong(id))
                rows += LocalMedia(itemUri, cursor.getString(name) ?: "", cursor.getLong(size),
                    if (duration < 0 || cursor.isNull(duration)) 0L else cursor.getLong(duration),
                    if (cursor.isNull(added)) 0L else cursor.getLong(added),
                    if(kind==MediaKind.Music) cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))?.takeIf { it!="<unknown>" } else null,
                    if(kind==MediaKind.Music) cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))?.takeIf { it!="<unknown>" } else null)
            }
        }
        rows
    }
    suspend fun groups(kind:MediaKind,grouping:MediaGrouping):List<MediaGroup> = withContext(Dispatchers.IO) {
        val collection=when(kind) {
            MediaKind.Photos -> MediaStore.Images.Media.getContentUri("external")
            MediaKind.Videos -> MediaStore.Video.Media.getContentUri("external")
            MediaKind.Music -> MediaStore.Audio.Media.getContentUri("external")
        }
        val idColumn=groupColumn(grouping)
        val nameColumn=groupNameColumn(grouping)
        val groups=linkedMapOf<String,MediaGroup>()
        val selection=(if(kind==MediaKind.Music) "is_music = 1" else "1 = 1")+
            (if(Build.VERSION.SDK_INT>=29) " AND is_pending = 0" else "")
        resolver.query(collection,arrayOf("_id",idColumn,nameColumn),selection,null,"date_added DESC")?.use { cursor ->
            while(cursor.moveToNext()) {
                val id=cursor.getString(1) ?: continue
                val title=cursor.getString(2)?.takeIf { it.isNotBlank() && it!="<unknown>" } ?: continue
                val old=groups[id]
                groups[id]=if(old!=null) old.copy(count=old.count+1) else MediaGroup(id,title,1,
                    ContentUris.withAppendedId(collection,cursor.getLong(0)))
            }
        }
        groups.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

}
