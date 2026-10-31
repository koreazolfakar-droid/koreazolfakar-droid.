package app.swiftdrop.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// SwiftMotion UI v1: a distinct signal-and-flow identity for SwiftDrop.
object SwiftDropDesign {
    val Signal = Color(0xFF006C5B)
    val Spark = Color(0xFFA6472F)
    val SparkHighlight = Color(0xFFF27B56)
    val Ink = Color(0xFF152326)
    val Background = Color(0xFFF5F8F5)
    // Legacy names retained for existing screen calls; use semantic names in new code.
    val Blue = Signal
    val Cyan = SparkHighlight
    val Navy = Ink
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ScreenPadding = 18.dp
    val SectionSpacing = 24.dp
    val ContentMaxWidth = 720.dp
    val Icon = 24.dp
    val TouchTarget = 48.dp
    const val MotionQuickMillis = 150
    const val MotionMillis = 240
    const val MotionEmphasisMillis = 300
}

private val LightColors = lightColorScheme(
    primary = SwiftDropDesign.Signal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F5E9), onPrimaryContainer = Color(0xFF103F34),
    secondary = SwiftDropDesign.Spark, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5D9), onSecondaryContainer = Color(0xFF6E2C1E),
    tertiary = Color(0xFF526B0D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F3C9), onTertiaryContainer = Color(0xFF344900),
    error = Color(0xFFB32635), onError = Color.White,
    errorContainer = Color(0xFFFFE8EA), onErrorContainer = Color(0xFF8D172A),
    background = SwiftDropDesign.Background, onBackground = SwiftDropDesign.Navy,
    surface = Color.White, onSurface = SwiftDropDesign.Navy,
    surfaceVariant = Color(0xFFEAF1EC), onSurfaceVariant = Color(0xFF4C625B),
    surfaceDim = Color(0xFFDCE6E0), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFCFA),
    surfaceContainer = Color(0xFFF0F5F1), surfaceContainerHigh = Color(0xFFE8F0EB),
    surfaceContainerHighest = Color(0xFFDFE9E3),
    outline = Color(0xFF647B72), outlineVariant = Color(0xFFD7E4DB),
    inverseSurface = Color(0xFF213733), inverseOnSurface = Color(0xFFE9F7EF),
    inversePrimary = Color(0xFF81F0D1), surfaceTint = SwiftDropDesign.Signal
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81F0D1), onPrimary = Color(0xFF063C33),
    primaryContainer = Color(0xFF164E43), onPrimaryContainer = Color(0xFFD4FBEF),
    secondary = Color(0xFFFFB096), onSecondary = Color(0xFF602A1D),
    secondaryContainer = Color(0xFF552E27), onSecondaryContainer = Color(0xFFFFD8CA),
    tertiary = Color(0xFFD2EB87), onTertiary = Color(0xFF2A3A0A),
    tertiaryContainer = Color(0xFF394D1E), onTertiaryContainer = Color(0xFFEBF9B4),
    error = Color(0xFFFFB1B8), onError = Color(0xFF660C1B),
    errorContainer = Color(0xFF51242C), onErrorContainer = Color(0xFFFFDDE0),
    background = Color(0xFF081317), onBackground = Color(0xFFE7F3EE),
    surface = Color(0xFF112125), onSurface = Color(0xFFE7F3EE),
    surfaceVariant = Color(0xFF253539), onSurfaceVariant = Color(0xFFABC6BC),
    surfaceDim = Color(0xFF0C191D), surfaceBright = Color(0xFF314347),
    surfaceContainerLowest = Color(0xFF081317), surfaceContainerLow = Color(0xFF0E1B1F),
    surfaceContainer = Color(0xFF16262A), surfaceContainerHigh = Color(0xFF1D3033),
    surfaceContainerHighest = Color(0xFF294044),
    outline = Color(0xFF8BA9A0), outlineVariant = Color(0xFF334A45),
    inverseSurface = Color(0xFFDCEDE6), inverseOnSurface = Color(0xFF1F3932),
    inversePrimary = Color(0xFF006C5B), surfaceTint = Color(0xFF81F0D1)
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

/**
 * Motion preference is read once per app lifecycle resume, not once per animation frame.
 * Sharing it through a composition local avoids a ContentResolver query for every media tile.
 */
private val LocalSwiftMotionEnabled = staticCompositionLocalOf { true }

private fun readMotionEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) > 0f
}.getOrDefault(true)

/** Honors Android's remove-animations setting for all SwiftMotion transitions. */
@Composable
fun swiftMotionDurationMillis(defaultMillis: Int = SwiftDropDesign.MotionMillis): Int =
    if (LocalSwiftMotionEnabled.current) defaultMillis else 0

@Composable
fun SwiftDropTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var motionEnabled by remember(context) { mutableStateOf(readMotionEnabled(context)) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                motionEnabled = readMotionEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalSwiftMotionEnabled provides motionEnabled) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(22.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp)
            ),
            content = content
        )
    }
}
