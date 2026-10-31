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

// SwiftDrop Graphite v2: premium neutral surfaces, restricted electric-blue signal.
// The old token aliases remain to protect existing screens and regression tests.
object SwiftDropDesign {
    val Signal = Color(0xFF315ED1)
    val Spark = Color(0xFF4E5B70)
    val SparkHighlight = Color(0xFF7294EF)
    val Ink = Color(0xFF172231)
    val Background = Color(0xFFF5F7FB)
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
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF15346E),
    secondary = SwiftDropDesign.Spark, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EDF3), onSecondaryContainer = Color(0xFF283444),
    tertiary = Color(0xFF267452), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCF2E6), onTertiaryContainer = Color(0xFF164B35),
    error = Color(0xFFAD2A40), onError = Color.White,
    errorContainer = Color(0xFFFFE6EB), onErrorContainer = Color(0xFF7E1E30),
    background = SwiftDropDesign.Background, onBackground = SwiftDropDesign.Ink,
    surface = Color.White, onSurface = SwiftDropDesign.Ink,
    surfaceVariant = Color(0xFFEDF1F7), onSurfaceVariant = Color(0xFF526173),
    surfaceDim = Color(0xFFE7ECF4), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCFDFF),
    surfaceContainer = Color(0xFFF0F3F9), surfaceContainerHigh = Color(0xFFE6EBF3),
    surfaceContainerHighest = Color(0xFFDCE4EF),
    outline = Color(0xFF64738A), outlineVariant = Color(0xFFDCE3EE),
    inverseSurface = Color(0xFF172231), inverseOnSurface = Color(0xFFF4F7FE),
    inversePrimary = Color(0xFFB3C8FF), surfaceTint = SwiftDropDesign.Signal
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFABC2FF), onPrimary = Color(0xFF102C69),
    primaryContainer = Color(0xFF23345A), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBBC7D9), onSecondary = Color(0xFF273447),
    secondaryContainer = Color(0xFF2B3545), onSecondaryContainer = Color(0xFFDFE6F1),
    tertiary = Color(0xFF79D3A7), onTertiary = Color(0xFF10442F),
    tertiaryContainer = Color(0xFF1C4335), onTertiaryContainer = Color(0xFFBEF1D6),
    error = Color(0xFFFFAEBB), onError = Color(0xFF651B29),
    errorContainer = Color(0xFF512936), onErrorContainer = Color(0xFFFFDDE4),
    background = Color(0xFF0B1019), onBackground = Color(0xFFEFF2F8),
    surface = Color(0xFF151C28), onSurface = Color(0xFFEFF2F8),
    surfaceVariant = Color(0xFF202A38), onSurfaceVariant = Color(0xFFABB7CA),
    surfaceDim = Color(0xFF0C121D), surfaceBright = Color(0xFF303B4A),
    surfaceContainerLowest = Color(0xFF0B1019), surfaceContainerLow = Color(0xFF111824),
    surfaceContainer = Color(0xFF182230), surfaceContainerHigh = Color(0xFF222D3D),
    surfaceContainerHighest = Color(0xFF2D3A4D),
    outline = Color(0xFF8898AF), outlineVariant = Color(0xFF354154),
    inverseSurface = Color(0xFFE5EBF5), inverseOnSurface = Color(0xFF192638),
    inversePrimary = SwiftDropDesign.Signal, surfaceTint = Color(0xFFABC2FF)
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
    lineHeight = height.sp, letterSpacing = 0.sp
)
private val AppTypography = Typography(
    displayLarge = type(40, 49, FontWeight.Medium),
    displayMedium = type(38, 46, FontWeight.Medium),
    displaySmall = type(34, 42, FontWeight.Medium),
    headlineLarge = type(28, 36, FontWeight.SemiBold),
    headlineMedium = type(24, 32, FontWeight.SemiBold),
    headlineSmall = type(22, 30, FontWeight.SemiBold),
    titleLarge = type(19, 27, FontWeight.SemiBold),
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
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(18.dp),
                extraLarge = RoundedCornerShape(22.dp)
            ),
            content = content
        )
    }
}
