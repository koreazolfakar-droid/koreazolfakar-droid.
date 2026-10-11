package app.swiftdrop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// SwiftDrop @feature Codex 11/10/2026 Shared visual tokens for every screen.
object SwiftDropDesign {
    val Blue = Color(0xFF1166F5)
    val Cyan = Color(0xFF06B6D4)
    val Navy = Color(0xFF0F172A)
    val Background = Color(0xFFF6F9FC)
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ContentMaxWidth = 720.dp
    val Icon = 24.dp
    val TouchTarget = 48.dp
    const val MotionMillis = 240
    const val RadarMillis = 1800
}

private val LightColors = lightColorScheme(
    primary = SwiftDropDesign.Blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF), onPrimaryContainer = Color(0xFF0742A9),
    secondary = Color(0xFF087B90), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F6FA), onSecondaryContainer = Color(0xFF075465),
    background = SwiftDropDesign.Background, onBackground = SwiftDropDesign.Navy,
    surface = Color.White, onSurface = SwiftDropDesign.Navy,
    surfaceVariant = Color(0xFFEAF0F7), onSurfaceVariant = Color(0xFF526078),
    outline = Color(0xFF738096), outlineVariant = Color(0xFFDCE4EE)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ABFFF), onPrimary = Color(0xFF003078),
    primaryContainer = Color(0xFF163560), onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFF6DD9EC), onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF13434C), onSecondaryContainer = Color(0xFFB8F0F8),
    background = SwiftDropDesign.Navy, onBackground = Color(0xFFF0F5FC),
    surface = Color(0xFF182338), onSurface = Color(0xFFF0F5FC),
    surfaceVariant = Color(0xFF25334B), onSurfaceVariant = Color(0xFFB7C5DA),
    outline = Color(0xFF8C9BB2), outlineVariant = Color(0xFF35445D)
)
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun SwiftDropTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)),
        content = content
    )
}
