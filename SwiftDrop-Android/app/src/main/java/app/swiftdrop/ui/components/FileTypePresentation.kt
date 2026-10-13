package app.swiftdrop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.swiftdrop.R
import java.util.Locale

/** One source of truth for document filters, format icons and send/history rows. */
enum class FileCategory(val filterKey: String, val shortLabel: String, val lightColor: Long, val darkColor: Long) {
    Pdf("PDF", "PDF", 0xFFCB303A, 0xFFFF727B),
    Word("Word", "DOC", 0xFF2369B5, 0xFF81B8FF),
    Sheets("Sheets", "XLS", 0xFF17804D, 0xFF69D5A0),
    Slides("Slides", "PPT", 0xFFC05B23, 0xFFFFAF78),
    Text("Text", "TXT", 0xFF63768E, 0xFFBDCCDE),
    Photo("Images", "IMG", 0xFFAC397B, 0xFFFF94CA),
    Video("Videos", "VID", 0xFF7745BD, 0xFFC6A1FF),
    Audio("Audio", "MP3", 0xFFCC4447, 0xFFFF9291),
    Package("Apps", "APK", 0xFF207F68, 0xFF8BD9C5),
    Archive("Archives", "ZIP", 0xFFAF7715, 0xFFFFD27A),
    Other("Other", "FILE", 0xFF687686, 0xFFBFC9D3)
}

/** Use extension first; providers often call office documents application/octet-stream. */
fun fileCategory(name: String, mimeType: String? = null): FileCategory {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val fromName = when (ext) {
        "pdf" -> FileCategory.Pdf
        "doc","docx","docm","dot","dotx","odt","rtf" -> FileCategory.Word
        "xls","xlsx","xlsm","xlsb","ods","csv","tsv" -> FileCategory.Sheets
        "ppt","pptx","pptm","pps","ppsx","odp" -> FileCategory.Slides
        "txt","text","md","log","json","xml","html","htm","epub","yaml","yml","ini","cfg" -> FileCategory.Text
        "jpg","jpeg","png","webp","gif","heic","heif","avif","bmp","tif","tiff","dng" -> FileCategory.Photo
        "mp4","mkv","mov","webm","avi","3gp","m4v","mpeg","mpg","ts" -> FileCategory.Video
        "mp3","wav","flac","ogg","opus","m4a","aac","wma","amr" -> FileCategory.Audio
        "apk","apks","xapk","aab" -> FileCategory.Package
        "zip","rar","7z","tar","gz","tgz","bz2","xz" -> FileCategory.Archive
        else -> null
    }
    if (fromName != null) return fromName
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when {
        mime == "application/pdf" -> FileCategory.Pdf
        mime.startsWith("image/") -> FileCategory.Photo
        mime.startsWith("video/") -> FileCategory.Video
        mime.startsWith("audio/") -> FileCategory.Audio
        mime.contains("spreadsheet") || mime.contains("excel") || mime == "text/csv" -> FileCategory.Sheets
        mime.contains("presentation") || mime.contains("powerpoint") -> FileCategory.Slides
        mime.contains("wordprocessing") || mime.contains("msword") -> FileCategory.Word
        mime.contains("zip") || mime.contains("compressed") || mime.contains("rar") -> FileCategory.Archive
        mime == "application/vnd.android.package-archive" -> FileCategory.Package
        mime.startsWith("text/") -> FileCategory.Text
        else -> FileCategory.Other
    }
}

/** File types the Documents screen accepts; folders stay navigable. */
fun isDocumentCategory(category: FileCategory): Boolean =
    category in setOf(FileCategory.Pdf,FileCategory.Word,FileCategory.Sheets,
        FileCategory.Slides,FileCategory.Text)

fun fileFilterMatches(name: String, mimeType: String?, selected: String, documentsOnly: Boolean): Boolean {
    val category = fileCategory(name,mimeType)
    return (!documentsOnly || isDocumentCategory(category)) &&
        (selected == "All" || selected == category.filterKey)
}

fun mimeTypesForFileFilter(selected: String, documentsOnly: Boolean): Array<String> =
    when(selected) {
        "PDF" -> arrayOf("application/pdf")
        "Word" -> arrayOf("application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text","application/rtf")
        "Sheets" -> arrayOf("application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet","text/csv")
        "Slides" -> arrayOf("application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.presentation")
        "Text" -> arrayOf("text/*","application/json","application/xml","application/epub+zip")
        "Images" -> arrayOf("image/*")
        "Videos" -> arrayOf("video/*")
        "Audio" -> arrayOf("audio/*")
        "Apps" -> arrayOf("application/vnd.android.package-archive")
        "Archives" -> arrayOf("application/zip","application/x-rar-compressed",
            "application/x-7z-compressed","application/gzip")
        "Other" -> arrayOf("*/*")
        else -> if (documentsOnly) arrayOf("application/pdf","text/*",
            "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text","application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation","application/epub+zip")
        else arrayOf("*/*")
    }

/**
 * Native, recognisable document identity without reusing Microsoft or Adobe trademark artwork.
 * Tonal icon surface follows light/dark Material scheme; file type remains legible at small size.
 */
@Composable
fun FileTypeBadge(
    name: String,
    modifier: Modifier = Modifier,
    mimeType: String? = null,
    folder: Boolean = false
) {
    if (folder) {
        CategoryBadge(R.drawable.ic_folder, modifier)
        return
    }
    val category = fileCategory(name,mimeType)
    val isDark = MaterialTheme.colorScheme.background.red +
        MaterialTheme.colorScheme.background.green +
        MaterialTheme.colorScheme.background.blue < 1.2f
    val accent = Color(if (isDark) category.darkColor else category.lightColor)
    val badge = when (category) {
        FileCategory.Sheets -> when(name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "csv","tsv" -> "CSV"
            else -> "XLS"
        }
        FileCategory.Word -> "DOC"
        FileCategory.Slides -> "PPT"
        FileCategory.Pdf -> "PDF"
        FileCategory.Archive -> "ZIP"
        FileCategory.Photo -> "IMG"
        FileCategory.Video -> "VID"
        FileCategory.Audio -> "MP3"
        FileCategory.Package -> "APK"
        FileCategory.Text -> "TXT"
        FileCategory.Other -> name.substringAfterLast('.', "").take(4)
            .uppercase(Locale.ROOT).ifBlank { "FILE" }
    }
    Surface(modifier.size(44.dp),shape=RoundedCornerShape(11.dp),color=accent.copy(alpha=.13f)) {
        Box(contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(1.dp)) {
                AppIcon(R.drawable.ic_documents,Modifier.size(20.dp),tint=accent)
                Text(badge,fontSize=9.sp,lineHeight=10.sp,fontWeight=FontWeight.Bold,
                    color=accent,maxLines=1)
            }
        }
    }
}
