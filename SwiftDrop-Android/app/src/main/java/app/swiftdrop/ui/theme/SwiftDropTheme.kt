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

// SwiftDrop @feature Codex 12/10/2026 Native Pro tokens shared by every destination.
object SwiftDropDesign {
    val Blue = Color(0xFF0864EE)
    val Cyan = Color(0xFF26D5E6)
    val Navy = Color(0xFF191C22)
    val Background = Color(0xFFF5F6F8)
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ScreenPadding = 16.dp
    val SectionSpacing = 24.dp
    val ContentMaxWidth = 720.dp
    val Icon = 24.dp
    val TouchTarget = 48.dp
    const val MotionMillis = 180
}

private val LightColors = lightColorScheme(
    primary = SwiftDropDesign.Blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE), onPrimaryContainer = Color(0xFF004EBE),
    secondary = Color(0xFF007C8A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5F7), onSecondaryContainer = Color(0xFF005C67),
    tertiary = Color(0xFF16764B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4F3EB), onTertiaryContainer = Color(0xFF155B3C),
    error = Color(0xFFBB2938), onError = Color.White,
    errorContainer = Color(0xFFFFE9EB), onErrorContainer = Color(0xFF8F1728),
    background = SwiftDropDesign.Background, onBackground = SwiftDropDesign.Navy,
    surface = Color.White, onSurface = SwiftDropDesign.Navy,
    surfaceVariant = Color(0xFFEEF0F4), onSurfaceVariant = Color(0xFF626875),
    surfaceDim = Color(0xFFE1E3E8), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF0F2F5), surfaceContainerHigh = Color(0xFFEAECF0),
    surfaceContainerHighest = Color(0xFFE4E7EB),
    outline = Color(0xFF777E89), outlineVariant = Color(0xFFE1E4EA),
    inverseSurface = Color(0xFF292D35), inverseOnSurface = Color(0xFFF4F5F7),
    inversePrimary = Color(0xFFA7C8FF), surfaceTint = SwiftDropDesign.Blue
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B2FF), onPrimary = Color(0xFF002B68),
    primaryContainer = Color(0xFF173456), onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFF6AD3DF), onSecondary = Color(0xFF00373E),
    secondaryContainer = Color(0xFF153B41), onSecondaryContainer = Color(0xFFADEBF1),
    tertiary = Color(0xFF79D6A2), onTertiary = Color(0xFF00381F),
    tertiaryContainer = Color(0xFF1D3D2C), onTertiaryContainer = Color(0xFFB9F2D0),
    error = Color(0xFFFF939D), onError = Color(0xFF5C1420),
    errorContainer = Color(0xFF46252B), onErrorContainer = Color(0xFFFFDADF),
    background = Color(0xFF101216), onBackground = Color(0xFFF0F1F4),
    surface = Color(0xFF1C1F25), onSurface = Color(0xFFF0F1F4),
    surfaceVariant = Color(0xFF282C34), onSurfaceVariant = Color(0xFFABB2BF),
    surfaceDim = Color(0xFF14161B), surfaceBright = Color(0xFF343841),
    surfaceContainerLowest = Color(0xFF101216), surfaceContainerLow = Color(0xFF191C21),
    surfaceContainer = Color(0xFF202329), surfaceContainerHigh = Color(0xFF292D34),
    surfaceContainerHighest = Color(0xFF333740),
    outline = Color(0xFF858D9B), outlineVariant = Color(0xFF343942),
    inverseSurface = Color(0xFFE5E8EE), inverseOnSurface = Color(0xFF252930),
    inversePrimary = Color(0xFF005ACC), surfaceTint = Color(0xFF82B2FF)
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
    lineHeight = height.sp, letterSpacing = 0.sp
)
private val AppTypography = Typography(
    displayLarge = type(44, 52, FontWeight.Medium),
    displayMedium = type(38, 46, FontWeight.Medium),
    displaySmall = type(34, 42, FontWeight.Medium),
    headlineLarge = type(30, 38, FontWeight.SemiBold),
    headlineMedium = type(26, 34, FontWeight.SemiBold),
    headlineSmall = type(23, 30, FontWeight.SemiBold),
    titleLarge = type(20, 28, FontWeight.SemiBold),
    titleMedium = type(16, 24, FontWeight.Medium),
    titleSmall = type(14, 20, FontWeight.Medium),
    bodyLarge = type(16, 24), bodyMedium = type(14, 21), bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.Medium),
    labelMedium = type(12, 18, FontWeight.Medium), labelSmall = type(11, 16, FontWeight.Medium)
)

@Composable
fun SwiftDropTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp)), content = content)
}
