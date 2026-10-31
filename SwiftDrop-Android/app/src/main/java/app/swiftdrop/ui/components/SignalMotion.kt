package app.swiftdrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import app.swiftdrop.ui.theme.swiftMotionDurationMillis

/**
 * SwiftMotion's signal-path motif: orbit for discovery and measured ring for transfer.
 * Never manufactures file progress. When no fraction is known, only the scanning arc moves.
 * Animation is disabled when Android's animator duration scale is zero.
 */
@Composable
fun SignalOrbit(
    fraction: Float?,
    active: Boolean,
    modifier: Modifier = Modifier,
    complete: Boolean = false,
    failed: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val motionEnabled = swiftMotionDurationMillis() > 0
    val turn = if (active && fraction == null && motionEnabled) {
        val transition = rememberInfiniteTransition(label = "SwiftMotion search")
        val angle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2300, easing = LinearEasing)),
            label = "signal sweep"
        )
        angle
    } else 0f

    val colors = MaterialTheme.colorScheme
    val accent = when {
        failed -> colors.error
        complete -> colors.tertiary
        else -> colors.primary
    }
    val trace = colors.secondary
    val track = colors.outlineVariant
    val knownProgress = fraction?.coerceIn(0f, 1f)
    val accessibleProgress = if (knownProgress != null || active) {
        Modifier.semantics {
            progressBarRangeInfo = if (knownProgress != null) {
                ProgressBarRangeInfo(knownProgress, 0f..1f)
            } else {
                ProgressBarRangeInfo.Indeterminate
            }
        }
    } else Modifier
    Box(modifier = modifier.then(accessibleProgress), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val width = 9.dp.toPx()
            val radius = (size.minDimension - width) / 2f - 6.dp.toPx()
            drawCircle(track, radius = radius, style = Stroke(width))
            drawCircle(
                accent.copy(alpha = if (active) .18f else .09f),
                radius = radius * .74f,
                style = Stroke(1.dp.toPx())
            )
            if (knownProgress != null) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(trace, accent, accent)),
                    startAngle = -90f,
                    sweepAngle = 360f * knownProgress,
                    useCenter = false,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            } else if (active) {
                drawArc(
                    color = accent,
                    startAngle = turn - 90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}
