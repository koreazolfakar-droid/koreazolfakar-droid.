package app.swiftdrop.ui.screens.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** Session-only QR; the complete barcode remains local and is never uploaded. */
@Composable
fun ReceiverQrCard(
    payload: String,
    title: Int = R.string.qr_receiver_title,
    hint: Int = R.string.qr_receiver_hint
) {
    val bitmap = remember(payload) {
        val matrix = MultiFormatWriter().encode(
            payload,BarcodeFormat.QR_CODE,480,480,mapOf(EncodeHintType.MARGIN to 2)
        )
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] =
                if (matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels,matrix.width,matrix.height,Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium)
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                val qrSize = (maxWidth - 24.dp).coerceAtMost(240.dp).coerceAtLeast(120.dp)
                Box(Modifier.background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                    .padding(12.dp),contentAlignment=Alignment.Center) {
                    Image(bitmap,contentDescription=stringResource(title),
                        modifier=Modifier.size(qrSize))
                }
            }
            Text(stringResource(hint),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
